import multer from 'multer';
import path from 'path';
import crypto from 'crypto';
import fs from 'fs';
import { ValidationError } from '@/utils/errors.js';
import { logger } from '@/utils/logger.js';

// Upload directory
const UPLOAD_DIR = process.env.UPLOAD_DIR || './uploads';
const AVATAR_DIR = path.join(UPLOAD_DIR, 'avatars');

// Ensure directories exist
[UPLOAD_DIR, AVATAR_DIR].forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
    logger.info(`Created upload directory: ${dir}`);
  }
});

// File filter for images
const imageFilter = (req: any, file: Express.Multer.File, cb: multer.FileFilterCallback) => {
  const allowedMimes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  const allowedExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp'];

  const ext = path.extname(file.originalname).toLowerCase();

  if (allowedMimes.includes(file.mimetype) && allowedExtensions.includes(ext)) {
    cb(null, true);
  } else {
    cb(new ValidationError('Only image files (JPEG, PNG, GIF, WebP) are allowed'));
  }
};

// Generate unique filename
const generateFilename = (file: Express.Multer.File): string => {
  const uniqueSuffix = crypto.randomBytes(8).toString('hex');
  const ext = path.extname(file.originalname).toLowerCase();
  return `${Date.now()}-${uniqueSuffix}${ext}`;
};

// Avatar storage configuration
const avatarStorage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, AVATAR_DIR);
  },
  filename: (req, file, cb) => {
    cb(null, generateFilename(file));
  }
});

// Memory storage (for processing before S3 upload, etc.)
const memoryStorage = multer.memoryStorage();

// Avatar upload configuration
export const avatarUpload = multer({
  storage: avatarStorage,
  fileFilter: imageFilter,
  limits: {
    fileSize: 5 * 1024 * 1024, // 5MB
    files: 1
  }
});

// Avatar upload with memory storage (for S3)
export const avatarUploadMemory = multer({
  storage: memoryStorage,
  fileFilter: imageFilter,
  limits: {
    fileSize: 5 * 1024 * 1024, // 5MB
    files: 1
  }
});

// Generic file upload
export const fileUpload = multer({
  storage: multer.diskStorage({
    destination: (req, file, cb) => {
      cb(null, UPLOAD_DIR);
    },
    filename: (req, file, cb) => {
      cb(null, generateFilename(file));
    }
  }),
  limits: {
    fileSize: 10 * 1024 * 1024 // 10MB
  }
});

// Export paths
export const uploadPaths = {
  base: UPLOAD_DIR,
  avatars: AVATAR_DIR
};

// Helper to delete old avatar
export const deleteOldAvatar = async (avatarPath: string): Promise<void> => {
  if (!avatarPath) return;

  try {
    // Extract filename from URL/path
    const filename = path.basename(avatarPath);
    const fullPath = path.join(AVATAR_DIR, filename);

    if (fs.existsSync(fullPath)) {
      fs.unlinkSync(fullPath);
      logger.info('Deleted old avatar', { path: fullPath });
    }
  } catch (error) {
    logger.warn('Failed to delete old avatar', { avatarPath, error });
  }
};

// Helper to get avatar URL
export const getAvatarUrl = (filename: string): string => {
  const baseUrl = process.env.BASE_URL || `http://localhost:${process.env.PORT || 3001}`;
  return `${baseUrl}/uploads/avatars/${filename}`;
};
