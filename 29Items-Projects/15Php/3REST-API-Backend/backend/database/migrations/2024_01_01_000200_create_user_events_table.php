<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Append-only behavioral telemetry feeding the recommendation engine.
     * Optimized for fast per-user aggregation, not updates.
     */
    public function up(): void
    {
        Schema::create('user_events', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('product_id')->constrained()->cascadeOnDelete();

            // view | add_to_cart | purchase | wishlist
            $table->string('type', 32);
            $table->float('weight')->default(1.0);
            $table->json('context')->nullable();

            $table->timestamps();

            // Build feature vectors by scanning a single user's events quickly.
            $table->index(['user_id', 'type']);
            $table->index(['product_id', 'type']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('user_events');
    }
};
