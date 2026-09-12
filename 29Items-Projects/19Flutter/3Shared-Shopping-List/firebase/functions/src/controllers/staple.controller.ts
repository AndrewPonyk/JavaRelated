import * as admin from 'firebase-admin';
import { HttpsError, CallableRequest } from 'firebase-functions/v2/https';

export interface ApplyTemplateRequest {
  templateId: string;
  targetListId: string;
}

export class StapleController {
  private db = admin.firestore();

  /**
   * Returns list of staple grocery templates with limit
   */
  async getTemplates() {
    const snap = await this.db.collection('staple_templates').limit(20).get();
    return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  }

  /**
   * Applies all staple items from a template into a target shopping list
   */
  async applyTemplateToList(request: CallableRequest<ApplyTemplateRequest>) {
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'User must be signed in.');
    }

    const { templateId, targetListId } = request.data || {};
    if (!templateId || typeof templateId !== 'string' || !targetListId || typeof targetListId !== 'string') {
      throw new HttpsError('invalid-argument', 'Valid templateId and targetListId strings are required.');
    }

    const sanitizedListId = targetListId.trim();
    const sanitizedTemplateId = templateId.trim();

    const listRef = this.db.collection('shopping_lists').doc(sanitizedListId);
    const listSnap = await listRef.get();
    if (!listSnap.exists) {
      throw new HttpsError('not-found', 'Target shopping list not found.');
    }

    const listData = listSnap.data()!;
    if (!listData.memberUids?.includes(request.auth.uid)) {
      throw new HttpsError('permission-denied', 'You are not an authorized member of this list.');
    }

    const templateRef = this.db.collection('staple_templates').doc(sanitizedTemplateId);
    const templateSnap = await templateRef.get();

    interface TemplateRawItem {
      name?: string;
      category?: string;
      storeSection?: string;
      quantity?: number;
      unit?: string;
      priceEstimate?: number;
    }

    let itemsToAdd: TemplateRawItem[] = [];
    if (templateSnap.exists) {
      itemsToAdd = templateSnap.data()?.items || [];
    } else {
      // Default fallback weekly staples
      itemsToAdd = [
        { name: 'Eggs (Dozen)', category: 'Dairy', storeSection: 'Dairy & Eggs', quantity: 1, unit: 'carton', priceEstimate: 4.99 },
        { name: 'Oat Milk', category: 'Dairy', storeSection: 'Dairy & Eggs', quantity: 2, unit: 'cartons', priceEstimate: 3.80 },
        { name: 'Sourdough Bread', category: 'Bakery', storeSection: 'Bakery & Bread', quantity: 1, unit: 'loaf', priceEstimate: 5.50 },
      ];
    }

    const batch = this.db.batch();
    for (const item of itemsToAdd) {
      const itemRef = listRef.collection('items').doc();
      const rawQty = Number(item.quantity);
      const safeQuantity = !isNaN(rawQty) && rawQty > 0 ? Math.min(rawQty, 999) : 1;
      const rawPrice = Number(item.priceEstimate);
      const safePrice = !isNaN(rawPrice) && rawPrice >= 0 ? Math.min(rawPrice, 10000) : 0;

      batch.set(itemRef, {
        id: itemRef.id,
        listId: sanitizedListId,
        name: String(item.name || 'Grocery Item').substring(0, 80),
        category: String(item.category || 'General').substring(0, 40),
        storeSection: String(item.storeSection || 'Produce & Fresh Herbs').substring(0, 50),
        quantity: safeQuantity,
        unit: String(item.unit || 'pcs').substring(0, 20),
        priceEstimate: safePrice,
        isGotIt: false,
        isArchived: false,
        addedByUid: request.auth.uid,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    await batch.commit();

    return {
      success: true,
      itemsAdded: itemsToAdd.length,
      listId: sanitizedListId,
    };
  }
}
