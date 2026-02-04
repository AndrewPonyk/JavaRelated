import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { NotFoundError, ValidationError, AuthorizationError, DatabaseError } from '@/utils/errors.js';
import crypto from 'crypto';

// Comment types
export interface DashboardComment {
  id: string;
  dashboardId: string;
  userId: string;
  content: string;
  parentId?: string;
  createdAt: Date;
  updatedAt: Date;
  isEdited: boolean;
  isDeleted: boolean;
  author: {
    id: string;
    email: string;
    firstName?: string;
    lastName?: string;
    avatar?: string;
  };
  replies?: DashboardComment[];
  replyCount: number;
  mentions: string[];
}

export interface CreateCommentData {
  content: string;
  parentId?: string;
  mentions?: string[];
}

export interface UpdateCommentData {
  content: string;
}

// In-memory comment storage (in production, use a dedicated table)
const dashboardComments = new Map<string, DashboardComment[]>();

export class CommentService {
  private static instance: CommentService;
  private readonly MAX_COMMENT_LENGTH = 2000;
  private readonly MAX_COMMENTS_PER_DASHBOARD = 1000;

  private constructor() {}

  static getInstance(): CommentService {
    if (!CommentService.instance) {
      CommentService.instance = new CommentService();
    }
    return CommentService.instance;
  }

  /**
   * Get comments for a dashboard
   */
  async getComments(
    dashboardId: string,
    userId: string,
    options?: { page?: number; limit?: number; parentId?: string }
  ): Promise<{ comments: DashboardComment[]; total: number }> {
    // Verify user has access to dashboard
    const hasAccess = await this.verifyDashboardAccess(dashboardId, userId, 'READ');
    if (!hasAccess) {
      throw new NotFoundError('Dashboard');
    }

    const allComments = dashboardComments.get(dashboardId) || [];
    const page = options?.page || 1;
    const limit = options?.limit || 20;

    // Filter by parentId (top-level comments only by default)
    let filteredComments = allComments.filter(c => !c.isDeleted);

    if (options?.parentId) {
      filteredComments = filteredComments.filter(c => c.parentId === options.parentId);
    } else {
      // Get only top-level comments
      filteredComments = filteredComments.filter(c => !c.parentId);
    }

    // Sort by createdAt descending (newest first)
    filteredComments.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

    // Add reply counts
    filteredComments = filteredComments.map(comment => ({
      ...comment,
      replyCount: allComments.filter(c => c.parentId === comment.id && !c.isDeleted).length
    }));

    // Paginate
    const start = (page - 1) * limit;
    const paginatedComments = filteredComments.slice(start, start + limit);

    return {
      comments: paginatedComments,
      total: filteredComments.length
    };
  }

  /**
   * Get a single comment with replies
   */
  async getComment(
    dashboardId: string,
    commentId: string,
    userId: string
  ): Promise<DashboardComment | null> {
    const hasAccess = await this.verifyDashboardAccess(dashboardId, userId, 'READ');
    if (!hasAccess) {
      throw new NotFoundError('Dashboard');
    }

    const allComments = dashboardComments.get(dashboardId) || [];
    const comment = allComments.find(c => c.id === commentId && !c.isDeleted);

    if (!comment) {
      return null;
    }

    // Add replies
    const replies = allComments
      .filter(c => c.parentId === commentId && !c.isDeleted)
      .sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());

    return {
      ...comment,
      replies,
      replyCount: replies.length
    };
  }

  /**
   * Add a comment to a dashboard
   */
  async addComment(
    dashboardId: string,
    userId: string,
    data: CreateCommentData
  ): Promise<DashboardComment> {
    // Verify user has at least read access (can comment if can view)
    const hasAccess = await this.verifyDashboardAccess(dashboardId, userId, 'READ');
    if (!hasAccess) {
      throw new NotFoundError('Dashboard');
    }

    // Validate content
    if (!data.content || data.content.trim().length === 0) {
      throw new ValidationError('Comment content is required');
    }

    if (data.content.length > this.MAX_COMMENT_LENGTH) {
      throw new ValidationError(`Comment cannot exceed ${this.MAX_COMMENT_LENGTH} characters`);
    }

    // Get user info
    const user = await prisma.user.findUnique({
      where: { id: userId },
      include: {
        profile: {
          select: { avatar: true }
        }
      }
    });

    if (!user) {
      throw new NotFoundError('User');
    }

    // Verify parent comment exists if replying
    if (data.parentId) {
      const allComments = dashboardComments.get(dashboardId) || [];
      const parentComment = allComments.find(c => c.id === data.parentId && !c.isDeleted);
      if (!parentComment) {
        throw new NotFoundError('Parent comment');
      }
    }

    // Extract mentions from content
    const mentions = this.extractMentions(data.content);

    // Create comment
    const comment: DashboardComment = {
      id: `comment-${crypto.randomBytes(8).toString('hex')}`,
      dashboardId,
      userId,
      content: data.content.trim(),
      parentId: data.parentId,
      createdAt: new Date(),
      updatedAt: new Date(),
      isEdited: false,
      isDeleted: false,
      author: {
        id: user.id,
        email: user.email,
        firstName: user.firstName || undefined,
        lastName: user.lastName || undefined,
        avatar: user.profile?.avatar || undefined
      },
      replyCount: 0,
      mentions
    };

    // Add to storage
    const comments = dashboardComments.get(dashboardId) || [];

    // Check max comments limit
    if (comments.length >= this.MAX_COMMENTS_PER_DASHBOARD) {
      throw new ValidationError('Maximum comments limit reached for this dashboard');
    }

    comments.push(comment);
    dashboardComments.set(dashboardId, comments);

    // Create notifications for mentioned users
    await this.notifyMentionedUsers(dashboardId, comment, mentions);

    // Notify parent comment author if this is a reply
    if (data.parentId) {
      const parentComment = comments.find(c => c.id === data.parentId);
      if (parentComment && parentComment.userId !== userId) {
        await this.createCommentNotification(
          parentComment.userId,
          dashboardId,
          comment.id,
          'reply',
          user
        );
      }
    }

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: data.parentId ? 'COMMENT_REPLY_ADDED' : 'COMMENT_ADDED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({
          commentId: comment.id,
          parentId: data.parentId,
          mentions: mentions.length
        })
      }
    });

    logBusinessEvent('DASHBOARD_COMMENT_ADDED', {
      dashboardId,
      userId,
      commentId: comment.id,
      isReply: !!data.parentId
    });

    return comment;
  }

  /**
   * Update a comment
   */
  async updateComment(
    dashboardId: string,
    commentId: string,
    userId: string,
    data: UpdateCommentData
  ): Promise<DashboardComment> {
    const comments = dashboardComments.get(dashboardId) || [];
    const commentIndex = comments.findIndex(c => c.id === commentId);

    if (commentIndex === -1) {
      throw new NotFoundError('Comment');
    }

    const comment = comments[commentIndex]!;

    // Only author can edit
    if (comment.userId !== userId) {
      // Check if admin
      const user = await prisma.user.findUnique({
        where: { id: userId },
        select: { role: true }
      });

      if (!user || !['SUPER_ADMIN', 'ADMIN'].includes(user.role)) {
        throw new AuthorizationError('Only the author can edit this comment');
      }
    }

    // Validate content
    if (!data.content || data.content.trim().length === 0) {
      throw new ValidationError('Comment content is required');
    }

    if (data.content.length > this.MAX_COMMENT_LENGTH) {
      throw new ValidationError(`Comment cannot exceed ${this.MAX_COMMENT_LENGTH} characters`);
    }

    // Update comment
    const newMentions = this.extractMentions(data.content);
    const updatedComment: DashboardComment = {
      ...comment,
      content: data.content.trim(),
      updatedAt: new Date(),
      isEdited: true,
      mentions: newMentions
    };

    comments[commentIndex] = updatedComment;
    dashboardComments.set(dashboardId, comments);

    // Notify newly mentioned users
    const newlyMentioned = newMentions.filter(m => !comment.mentions.includes(m));
    if (newlyMentioned.length > 0) {
      const user = await prisma.user.findUnique({
        where: { id: userId },
        select: { id: true, email: true, firstName: true, lastName: true }
      });
      if (user) {
        await this.notifyMentionedUsers(dashboardId, updatedComment, newlyMentioned);
      }
    }

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'COMMENT_UPDATED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({ commentId })
      }
    });

    return updatedComment;
  }

  /**
   * Delete a comment
   */
  async deleteComment(
    dashboardId: string,
    commentId: string,
    userId: string
  ): Promise<boolean> {
    const comments = dashboardComments.get(dashboardId) || [];
    const commentIndex = comments.findIndex(c => c.id === commentId);

    if (commentIndex === -1) {
      return false;
    }

    const comment = comments[commentIndex]!;

    // Only author or admin can delete
    if (comment.userId !== userId) {
      const user = await prisma.user.findUnique({
        where: { id: userId },
        select: { role: true }
      });

      if (!user || !['SUPER_ADMIN', 'ADMIN'].includes(user.role)) {
        throw new AuthorizationError('Only the author can delete this comment');
      }
    }

    // Soft delete
    comments[commentIndex] = {
      ...comment,
      isDeleted: true,
      content: '[deleted]',
      updatedAt: new Date()
    };

    dashboardComments.set(dashboardId, comments);

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'COMMENT_DELETED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({ commentId })
      }
    });

    logBusinessEvent('DASHBOARD_COMMENT_DELETED', {
      dashboardId,
      userId,
      commentId
    });

    return true;
  }

  /**
   * Get comment count for a dashboard
   */
  async getCommentCount(dashboardId: string): Promise<number> {
    const comments = dashboardComments.get(dashboardId) || [];
    return comments.filter(c => !c.isDeleted).length;
  }

  // Helper methods
  private extractMentions(content: string): string[] {
    // Extract @mentions from content
    const mentionRegex = /@([a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/g;
    const matches = content.match(mentionRegex) || [];
    return [...new Set(matches.map(m => m.substring(1)))]; // Remove @ prefix
  }

  private async notifyMentionedUsers(
    dashboardId: string,
    comment: DashboardComment,
    mentions: string[]
  ): Promise<void> {
    if (mentions.length === 0) return;

    // Find mentioned users
    const mentionedUsers = await prisma.user.findMany({
      where: {
        email: { in: mentions },
        isActive: true
      },
      select: { id: true, email: true }
    });

    // Create notifications
    for (const user of mentionedUsers) {
      if (user.id !== comment.userId) {
        await this.createCommentNotification(
          user.id,
          dashboardId,
          comment.id,
          'mention',
          comment.author
        );
      }
    }
  }

  private async createCommentNotification(
    recipientId: string,
    dashboardId: string,
    commentId: string,
    type: 'mention' | 'reply',
    author: any
  ): Promise<void> {
    try {
      const dashboard = await prisma.dashboard.findUnique({
        where: { id: dashboardId },
        select: { title: true }
      });

      const authorName = author.firstName
        ? `${author.firstName} ${author.lastName || ''}`.trim()
        : author.email;

      const title = type === 'mention'
        ? `${authorName} mentioned you in a comment`
        : `${authorName} replied to your comment`;

      await prisma.notification.create({
        data: {
          title,
          message: `On dashboard: ${dashboard?.title || 'Unknown'}`,
          type: type === 'mention' ? 'MENTION' : 'COMMENT',
          userId: recipientId,
          entityType: 'comment',
          entityId: commentId,
          actionUrl: `/dashboard/${dashboardId}?comment=${commentId}`,
          actionLabel: 'View Comment'
        }
      });
    } catch (error) {
      logger.warn('Failed to create comment notification', {
        recipientId,
        dashboardId,
        commentId,
        error
      });
    }
  }

  private async verifyDashboardAccess(
    dashboardId: string,
    userId: string,
    requiredPermission: 'READ' | 'WRITE' | 'ADMIN'
  ): Promise<boolean> {
    const dashboard = await prisma.dashboard.findFirst({
      where: {
        id: dashboardId,
        OR: [
          { userId },
          { isPublic: true },
          {
            dashboardShares: {
              some: { userId }
            }
          }
        ]
      }
    });

    return !!dashboard;
  }

  /**
   * Delete all comments for a dashboard (when dashboard is deleted)
   */
  async deleteAllComments(dashboardId: string): Promise<void> {
    dashboardComments.delete(dashboardId);
  }
}

// Export singleton instance
export const commentService = CommentService.getInstance();
