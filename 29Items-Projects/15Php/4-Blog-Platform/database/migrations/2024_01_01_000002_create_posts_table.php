<?php

declare(strict_types=1);

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('posts', function (Blueprint $table): void {
            $table->id();

            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('category_id')->nullable()->constrained()->nullOnDelete();

            $table->string('title');
            $table->string('slug')->unique();
            $table->string('excerpt', 300)->nullable();

            $table->longText('body');                 // raw Markdown source
            $table->longText('body_html')->nullable(); // sanitised, render-on-save HTML

            $table->unsignedSmallInteger('reading_time')->default(1); // minutes

            // SEO overrides (fall back to derived values when null).
            $table->string('meta_title')->nullable();
            $table->string('meta_description', 300)->nullable();
            $table->string('og_image')->nullable();

            $table->string('status')->default('draft'); // draft | published
            $table->timestamp('published_at')->nullable();

            $table->timestamps();

            // Hot read paths: list by status+date, and filter by category.
            $table->index(['status', 'published_at']);
            $table->index('category_id');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('posts');
    }
};
