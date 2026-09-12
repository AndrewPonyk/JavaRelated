import * as admin from 'firebase-admin';
import { onCall, onRequest } from 'firebase-functions/v2/https';
import { onDocumentWritten } from 'firebase-functions/v2/firestore';
import { InvitationController } from './controllers/invitation.controller';
import { StapleController } from './controllers/staple.controller';

// Initialize Firebase Admin SDK
admin.initializeApp();

const invitationController = new InvitationController();
const stapleController = new StapleController();

/**
 * Health check endpoint for container orchestrators and uptime probes
 * Responds with HTTP 200 OK and service metadata
 */
export const healthCheck = onRequest(
  { cors: true },
  (_req, res) => {
    res.status(200).json({
      status: 'healthy',
      service: 'shared-shopping-list-api',
      timestamp: new Date().toISOString(),
      uptimeSeconds: Math.floor(process.uptime()),
      environment: process.env.APP_ENV || 'production',
    });
  }
);

/**
 * Callable endpoint to create a shareable invitation link
 */
export const createInvitation = onCall(
  { cors: true },
  async (request) => {
    return await invitationController.createInvitation(request);
  }
);

/**
 * Callable endpoint to redeem an invitation token and join a list
 */
export const redeemInvitation = onCall(
  { cors: true },
  async (request) => {
    return await invitationController.redeemInvitation(request);
  }
);

/**
 * Callable endpoint to fetch staple templates
 */
export const getTemplates = onCall(
  { cors: true },
  async () => {
    return await stapleController.getTemplates();
  }
);

/**
 * Callable endpoint to apply staple template items to a shopping list
 */
export const applyTemplateToList = onCall(
  { cors: true },
  async (request) => {
    return await stapleController.applyTemplateToList(request);
  }
);

/**
 * Firestore trigger: Sends push notifications when items are checked off or added
 */
export const onItemUpdated = onDocumentWritten(
  'shopping_lists/{listId}/items/{itemId}',
  async (event) => {
    const listId = event.params.listId;
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();

    // If item was deleted, nothing to notify
    if (!afterData) return;

    // Check if "gotIt" status transitioned to true
    const justCheckedOff = !beforeData?.isGotIt && afterData.isGotIt;
    if (!justCheckedOff) return;

    const db = admin.firestore();
    const listSnap = await db.collection('shopping_lists').doc(listId).get();
    if (!listSnap.exists) return;

    const listData = listSnap.data()!;
    const memberUids: string[] = listData.memberUids || [];
    const actorUid = afterData.gotItByUid || afterData.updatedByUid;

    // Find FCM tokens for members other than the one who checked it off
    const tokensToSend: string[] = [];
    for (const uid of memberUids) {
      if (uid === actorUid) continue;
      const userSnap = await db.collection('users').doc(uid).get();
      if (userSnap.exists) {
        const tokens: string[] = userSnap.data()?.fcmTokens || [];
        tokensToSend.push(...tokens);
      }
    }

    if (tokensToSend.length === 0) return;

    // Send multicast push alert
    const message: admin.messaging.MulticastMessage = {
      tokens: tokensToSend,
      notification: {
        title: 'Item Checked Off!',
        body: `"${afterData.name}" was picked up in ${listData.name}.`,
      },
      data: {
        listId,
        itemId: event.params.itemId,
        click_action: 'FLUTTER_NOTIFICATION_CLICK',
      },
    };

    try {
      await admin.messaging().sendEachForMulticast(message);
    } catch (err) {
      console.error('Failed sending push notification:', err);
    }
  }
);
