import * as admin from 'firebase-admin';
import { HttpsError, CallableRequest } from 'firebase-functions/v2/https';

export interface CreateInvitationRequest {
  listId: string;
  role: 'editor' | 'viewer';
  expiresInDays?: number;
}

export interface RedeemInvitationRequest {
  token: string;
}

/**
 * Controller managing secure list invitations and deep link redemption
 */
export class InvitationController {
  private db = admin.firestore();

  /**
   * Generates a new invitation token for a shopping list.
   * Input validation: ensures authenticated caller is an owner/member of the list.
   */
  async createInvitation(request: CallableRequest<CreateInvitationRequest>) {
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'User must be signed in to create invitations.');
    }

    const { listId, role = 'editor', expiresInDays = 7 } = request.data || {};

    // Input Sanitization & Validation
    if (!listId || typeof listId !== 'string' || listId.trim().length === 0 || listId.length > 128) {
      throw new HttpsError('invalid-argument', 'Valid listId (max 128 chars) is required.');
    }

    const sanitizedListId = listId.trim();

    if (role !== 'editor' && role !== 'viewer') {
      throw new HttpsError('invalid-argument', 'Role must be either "editor" or "viewer".');
    }

    const listRef = this.db.collection('shopping_lists').doc(sanitizedListId);
    const listSnap = await listRef.get();

    if (!listSnap.exists) {
      throw new HttpsError('not-found', 'Target shopping list does not exist.');
    }

    const listData = listSnap.data()!;
    if (!listData.memberUids?.includes(request.auth.uid)) {
      throw new HttpsError('permission-denied', 'Only existing list members can invite others.');
    }

    // Generate random secure token
    const token = admin.firestore().collection('_').doc().id + admin.firestore().collection('_').doc().id;
    const boundedDays = Math.min(Math.max(Number(expiresInDays) || 7, 1), 30);
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + boundedDays);

    const invitationData = {
      token,
      listId: sanitizedListId,
      listName: String(listData.name || 'Shared Shopping List').substring(0, 100),
      invitedByUid: request.auth.uid,
      invitedByName: String(request.auth.token.name || 'Household Member').substring(0, 80),
      role,
      maxUses: 5,
      usedCount: 0,
      expiresAt: admin.firestore.Timestamp.fromDate(expiresAt),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await this.db.collection('invitations').doc(token).set(invitationData);

    return {
      token,
      listId: sanitizedListId,
      inviteUrl: `https://shoppinglist.page.link/join?token=${token}`,
      expiresAt: expiresAt.toISOString(),
    };
  }

  /**
   * Redeems an invitation token via deep link and joins the user to the list.
   * Atomic Firestore Transaction ensures token is not overused and membership is granted.
   */
  async redeemInvitation(request: CallableRequest<RedeemInvitationRequest>) {
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'User must be signed in to redeem an invitation.');
    }

    const { token } = request.data || {};
    if (!token || typeof token !== 'string' || !/^[a-zA-Z0-9_-]{16,128}$/.test(token.trim())) {
      throw new HttpsError('invalid-argument', 'Valid alphanumeric invitation token is required.');
    }

    const sanitizedToken = token.trim();
    const userId = request.auth.uid;
    const inviteRef = this.db.collection('invitations').doc(sanitizedToken);

    return await this.db.runTransaction(async (transaction) => {
      const inviteSnap = await transaction.get(inviteRef);
      if (!inviteSnap.exists) {
        throw new HttpsError('not-found', 'Invalid or non-existent invitation token.');
      }

      const invite = inviteSnap.data()!;
      const now = admin.firestore.Timestamp.now();

      if (invite.expiresAt && invite.expiresAt.toMillis() < now.toMillis()) {
        throw new HttpsError('failed-precondition', 'This invitation has expired.');
      }

      if (invite.usedCount >= invite.maxUses) {
        throw new HttpsError('resource-exhausted', 'This invitation link has reached its maximum uses.');
      }

      const listRef = this.db.collection('shopping_lists').doc(invite.listId);
      const listSnap = await transaction.get(listRef);

      if (!listSnap.exists) {
        throw new HttpsError('not-found', 'The list associated with this invitation no longer exists.');
      }

      // Add user to memberUids and set their role in memberRoles map
      transaction.update(listRef, {
        memberUids: admin.firestore.FieldValue.arrayUnion(userId),
        [`memberRoles.${userId}`]: invite.role,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      // Increment token usage
      transaction.update(inviteRef, {
        usedCount: admin.firestore.FieldValue.increment(1),
      });

      return {
        success: true,
        listId: invite.listId,
        listName: invite.listName,
        role: invite.role,
      };
    });
  }
}
