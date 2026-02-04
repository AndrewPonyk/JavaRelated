import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { NotFoundError, ValidationError, DatabaseError } from '@/utils/errors.js';
import crypto from 'crypto';
import path from 'path';
import fs from 'fs/promises';

// Export status types
export type ExportStatus = 'pending' | 'processing' | 'completed' | 'failed';
export type ExportFormat = 'pdf' | 'png' | 'csv' | 'json';

export interface ExportJob {
  id: string;
  dashboardId: string;
  userId: string;
  format: ExportFormat;
  status: ExportStatus;
  progress: number;
  downloadUrl?: string;
  errorMessage?: string;
  createdAt: Date;
  completedAt?: Date;
  expiresAt: Date;
  options?: ExportOptions;
}

export interface ExportOptions {
  quality?: 'low' | 'medium' | 'high';
  includeWidgets?: boolean;
  dateRange?: {
    start: string;
    end: string;
  };
  paperSize?: 'a4' | 'letter' | 'legal';
  orientation?: 'portrait' | 'landscape';
}

// In-memory job storage (in production, use Redis or a job queue like Bull)
const exportJobs = new Map<string, ExportJob>();

// Export directory
const EXPORT_DIR = process.env.EXPORT_DIR || './exports';

export class ExportService {
  private static instance: ExportService;

  private constructor() {
    this.ensureExportDir();
  }

  static getInstance(): ExportService {
    if (!ExportService.instance) {
      ExportService.instance = new ExportService();
    }
    return ExportService.instance;
  }

  private async ensureExportDir(): Promise<void> {
    try {
      await fs.mkdir(EXPORT_DIR, { recursive: true });
    } catch (error) {
      logger.error('Failed to create export directory', { error });
    }
  }

  /**
   * Create a new export job
   */
  async createExportJob(
    dashboardId: string,
    userId: string,
    format: ExportFormat,
    options?: ExportOptions
  ): Promise<ExportJob> {
    // Verify dashboard exists and user has access
    const dashboard = await prisma.dashboard.findFirst({
      where: {
        id: dashboardId,
        OR: [
          { userId },
          { isPublic: true },
          { dashboardShares: { some: { userId } } }
        ]
      },
      include: {
        widgets: true,
        user: {
          select: { id: true, email: true, firstName: true, lastName: true }
        }
      }
    });

    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    // Validate format
    if (!['pdf', 'png', 'csv', 'json'].includes(format)) {
      throw new ValidationError(`Invalid export format: ${format}`);
    }

    // Generate export job ID
    const exportId = `export-${crypto.randomBytes(8).toString('hex')}`;

    // Create export job
    const job: ExportJob = {
      id: exportId,
      dashboardId,
      userId,
      format,
      status: 'pending',
      progress: 0,
      createdAt: new Date(),
      expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000), // 24 hours
      options
    };

    // Store in memory (and cache for persistence)
    exportJobs.set(exportId, job);
    await cacheService.setExportStatus(exportId, job, 24 * 60 * 60); // 24 hours TTL

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'EXPORT_INITIATED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({ format, exportId, options })
      }
    });

    logBusinessEvent('DASHBOARD_EXPORT_INITIATED', {
      dashboardId,
      userId,
      format,
      exportId
    });

    // Start async processing
    this.processExportJob(exportId, dashboard).catch(error => {
      logger.error('Export job processing failed', { exportId, error });
    });

    return job;
  }

  /**
   * Get export job status
   */
  async getExportStatus(exportId: string, userId: string): Promise<ExportJob | null> {
    // Try memory first
    let job = exportJobs.get(exportId);

    // Fall back to cache
    if (!job) {
      job = await cacheService.getExportStatus(exportId);
    }

    if (!job) {
      return null;
    }

    // Verify user owns the export
    if (job.userId !== userId) {
      // Check if user is admin
      const user = await prisma.user.findUnique({
        where: { id: userId },
        select: { role: true }
      });

      if (!user || !['SUPER_ADMIN', 'ADMIN'].includes(user.role)) {
        return null;
      }
    }

    return job;
  }

  /**
   * Process the export job (runs in background)
   */
  private async processExportJob(exportId: string, dashboard: any): Promise<void> {
    const job = exportJobs.get(exportId);
    if (!job) return;

    try {
      // Update status to processing
      job.status = 'processing';
      job.progress = 10;
      await this.updateJobStatus(job);

      let result: ExportResult;

      switch (job.format) {
        case 'pdf':
          result = await this.exportToPdf(dashboard, job);
          break;
        case 'png':
          result = await this.exportToPng(dashboard, job);
          break;
        case 'csv':
          result = await this.exportToCsv(dashboard, job);
          break;
        case 'json':
          result = await this.exportToJson(dashboard, job);
          break;
        default:
          throw new ValidationError(`Unsupported format: ${job.format}`);
      }

      // Update job as completed
      job.status = 'completed';
      job.progress = 100;
      job.downloadUrl = result.downloadUrl;
      job.completedAt = new Date();
      await this.updateJobStatus(job);

      logBusinessEvent('DASHBOARD_EXPORT_COMPLETED', {
        dashboardId: dashboard.id,
        userId: job.userId,
        format: job.format,
        exportId: job.id
      });

    } catch (error) {
      job.status = 'failed';
      job.errorMessage = error instanceof Error ? error.message : 'Export failed';
      await this.updateJobStatus(job);

      logger.error('Export job failed', { exportId, error });
    }
  }

  private async updateJobStatus(job: ExportJob): Promise<void> {
    exportJobs.set(job.id, job);
    await cacheService.setExportStatus(job.id, job, 24 * 60 * 60);
  }

  /**
   * Export dashboard to PDF format
   */
  private async exportToPdf(dashboard: any, job: ExportJob): Promise<ExportResult> {
    job.progress = 30;
    await this.updateJobStatus(job);

    // Generate PDF content (simplified - in production use puppeteer or similar)
    const pdfContent = this.generatePdfContent(dashboard, job.options);

    job.progress = 70;
    await this.updateJobStatus(job);

    // Save to file
    const filename = `${dashboard.slug || dashboard.id}-${Date.now()}.pdf`;
    const filepath = path.join(EXPORT_DIR, filename);

    await fs.writeFile(filepath, pdfContent);

    job.progress = 90;
    await this.updateJobStatus(job);

    return {
      downloadUrl: `/api/exports/${job.id}/download`,
      filename,
      filepath,
      size: pdfContent.length
    };
  }

  /**
   * Export dashboard to PNG format
   */
  private async exportToPng(dashboard: any, job: ExportJob): Promise<ExportResult> {
    job.progress = 30;
    await this.updateJobStatus(job);

    // Generate PNG (simplified - in production use puppeteer or sharp)
    const pngContent = this.generatePngPlaceholder(dashboard);

    job.progress = 70;
    await this.updateJobStatus(job);

    const filename = `${dashboard.slug || dashboard.id}-${Date.now()}.png`;
    const filepath = path.join(EXPORT_DIR, filename);

    await fs.writeFile(filepath, pngContent);

    job.progress = 90;
    await this.updateJobStatus(job);

    return {
      downloadUrl: `/api/exports/${job.id}/download`,
      filename,
      filepath,
      size: pngContent.length
    };
  }

  /**
   * Export dashboard to CSV format
   */
  private async exportToCsv(dashboard: any, job: ExportJob): Promise<ExportResult> {
    job.progress = 30;
    await this.updateJobStatus(job);

    // Generate CSV from widget data
    const csvContent = this.generateCsvContent(dashboard);

    job.progress = 70;
    await this.updateJobStatus(job);

    const filename = `${dashboard.slug || dashboard.id}-${Date.now()}.csv`;
    const filepath = path.join(EXPORT_DIR, filename);

    await fs.writeFile(filepath, csvContent, 'utf-8');

    job.progress = 90;
    await this.updateJobStatus(job);

    return {
      downloadUrl: `/api/exports/${job.id}/download`,
      filename,
      filepath,
      size: Buffer.byteLength(csvContent)
    };
  }

  /**
   * Export dashboard to JSON format
   */
  private async exportToJson(dashboard: any, job: ExportJob): Promise<ExportResult> {
    job.progress = 30;
    await this.updateJobStatus(job);

    // Export dashboard configuration and data
    const jsonContent = JSON.stringify({
      dashboard: {
        id: dashboard.id,
        title: dashboard.title,
        description: dashboard.description,
        layout: dashboard.layout,
        settings: dashboard.settings,
        createdAt: dashboard.createdAt,
        updatedAt: dashboard.updatedAt
      },
      widgets: dashboard.widgets.map((w: any) => ({
        id: w.id,
        title: w.title,
        type: w.type,
        config: w.config,
        position: w.position,
        dataSource: w.dataSource,
        query: w.query
      })),
      exportedAt: new Date().toISOString(),
      exportedBy: job.userId,
      version: '1.0'
    }, null, 2);

    job.progress = 70;
    await this.updateJobStatus(job);

    const filename = `${dashboard.slug || dashboard.id}-${Date.now()}.json`;
    const filepath = path.join(EXPORT_DIR, filename);

    await fs.writeFile(filepath, jsonContent, 'utf-8');

    job.progress = 90;
    await this.updateJobStatus(job);

    return {
      downloadUrl: `/api/exports/${job.id}/download`,
      filename,
      filepath,
      size: Buffer.byteLength(jsonContent)
    };
  }

  /**
   * Get export file for download
   */
  async getExportFile(exportId: string, userId: string): Promise<{ filepath: string; filename: string; mimetype: string } | null> {
    const job = await this.getExportStatus(exportId, userId);

    if (!job || job.status !== 'completed' || !job.downloadUrl) {
      return null;
    }

    // Find the file
    const files = await fs.readdir(EXPORT_DIR);
    const dashboardId = job.dashboardId;

    const matchingFile = files.find(f =>
      f.includes(dashboardId) && f.endsWith(`.${job.format}`)
    );

    if (!matchingFile) {
      return null;
    }

    const mimetypes: Record<ExportFormat, string> = {
      pdf: 'application/pdf',
      png: 'image/png',
      csv: 'text/csv',
      json: 'application/json'
    };

    return {
      filepath: path.join(EXPORT_DIR, matchingFile),
      filename: matchingFile,
      mimetype: mimetypes[job.format]
    };
  }

  // Helper methods for content generation
  private generatePdfContent(dashboard: any, options?: ExportOptions): Buffer {
    // Simplified PDF generation - in production, use a proper PDF library
    const content = `%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]
/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 200 >>
stream
BT
/F1 24 Tf
50 700 Td
(${dashboard.title}) Tj
ET
BT
/F1 12 Tf
50 670 Td
(${dashboard.description || 'Dashboard Export'}) Tj
ET
BT
/F1 10 Tf
50 640 Td
(Widgets: ${dashboard.widgets?.length || 0}) Tj
ET
BT
/F1 10 Tf
50 620 Td
(Exported: ${new Date().toISOString()}) Tj
ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
0000000266 00000 n
0000000518 00000 n
trailer
<< /Size 6 /Root 1 0 R >>
startxref
595
%%EOF`;
    return Buffer.from(content);
  }

  private generatePngPlaceholder(dashboard: any): Buffer {
    // Generate a simple 1x1 pixel PNG as placeholder
    // In production, use puppeteer to capture actual dashboard screenshot
    const pngHeader = Buffer.from([
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, // PNG signature
      0x00, 0x00, 0x00, 0x0d, // IHDR length
      0x49, 0x48, 0x44, 0x52, // IHDR
      0x00, 0x00, 0x00, 0x01, // width: 1
      0x00, 0x00, 0x00, 0x01, // height: 1
      0x08, 0x02, // bit depth: 8, color type: 2 (RGB)
      0x00, 0x00, 0x00, // compression, filter, interlace
      0x90, 0x77, 0x53, 0xde, // IHDR CRC
      0x00, 0x00, 0x00, 0x0c, // IDAT length
      0x49, 0x44, 0x41, 0x54, // IDAT
      0x08, 0xd7, 0x63, 0xf8, 0xff, 0xff, 0xff, 0x00, 0x05, 0xfe, 0x02, 0xfe,
      0xa3, 0x6c, 0x00, 0x01, // IDAT data + CRC
      0x00, 0x00, 0x00, 0x00, // IEND length
      0x49, 0x45, 0x4e, 0x44, // IEND
      0xae, 0x42, 0x60, 0x82  // IEND CRC
    ]);
    return pngHeader;
  }

  private generateCsvContent(dashboard: any): string {
    const rows: string[] = [];

    // Header
    rows.push('Dashboard Export');
    rows.push(`Title,${this.escapeCsv(dashboard.title)}`);
    rows.push(`Description,${this.escapeCsv(dashboard.description || '')}`);
    rows.push(`Created,${dashboard.createdAt}`);
    rows.push(`Widgets,${dashboard.widgets?.length || 0}`);
    rows.push('');

    // Widgets
    if (dashboard.widgets?.length > 0) {
      rows.push('Widget ID,Title,Type,Data Source');
      dashboard.widgets.forEach((widget: any) => {
        rows.push([
          widget.id,
          this.escapeCsv(widget.title),
          widget.type,
          this.escapeCsv(widget.dataSource || '')
        ].join(','));
      });
    }

    return rows.join('\n');
  }

  private escapeCsv(value: string): string {
    if (value.includes(',') || value.includes('"') || value.includes('\n')) {
      return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
  }

  /**
   * Clean up expired exports
   */
  async cleanupExpiredExports(): Promise<number> {
    const now = new Date();
    let cleaned = 0;

    for (const [exportId, job] of exportJobs.entries()) {
      if (job.expiresAt < now) {
        exportJobs.delete(exportId);
        cleaned++;
      }
    }

    // Also clean up files older than 24 hours
    try {
      const files = await fs.readdir(EXPORT_DIR);
      const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;

      for (const file of files) {
        const filepath = path.join(EXPORT_DIR, file);
        const stats = await fs.stat(filepath);

        if (stats.mtimeMs < oneDayAgo) {
          await fs.unlink(filepath);
          cleaned++;
        }
      }
    } catch (error) {
      logger.warn('Failed to cleanup export files', { error });
    }

    if (cleaned > 0) {
      logger.info('Cleaned up expired exports', { count: cleaned });
    }

    return cleaned;
  }
}

interface ExportResult {
  downloadUrl: string;
  filename: string;
  filepath: string;
  size: number;
}

// Export singleton instance
export const exportService = ExportService.getInstance();
