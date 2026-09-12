<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Precomputed, denormalized recommendations per user. Written by the
     * (async/scheduled) clustering job and read directly — never computed
     * inside a request (TECH-NOTES pitfall #10).
     */
    public function up(): void
    {
        Schema::create('recommendations', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('product_id')->constrained()->cascadeOnDelete();

            $table->unsignedInteger('cluster')->index(); // assigned behavioral cluster
            $table->float('score');                       // ranking score (higher = better)
            $table->timestamp('computed_at')->useCurrent();

            $table->timestamps();

            // One row per (user, product); fetch a user's top-N by score.
            $table->unique(['user_id', 'product_id']);
            $table->index(['user_id', 'score']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('recommendations');
    }
};
