<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('products', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')          // seller / owner
                ->constrained()
                ->cascadeOnDelete();

            $table->string('name');
            $table->text('description')->nullable();

            // Money as integer minor units — never float (TECH-NOTES pitfall #12).
            $table->unsignedBigInteger('price_cents')->default(0);
            $table->char('currency', 3)->default('USD');

            $table->string('category')->index();   // filtered often → indexed
            $table->unsignedInteger('stock')->default(0);
            $table->boolean('is_active')->default(true)->index();

            $table->timestamps();
            $table->softDeletes();

            // Composite index for the common "active products in a category" query.
            $table->index(['is_active', 'category']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('products');
    }
};
