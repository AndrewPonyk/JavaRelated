<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

class CreateArticlesTable extends Migration
{
    /**
     * Run the migrations.
     *
     * @return void
     */
    public function up()
    {
        Schema::create('articles', function (Blueprint $table) {
            $table->id();
            
            // Core Identity
            $table->string('title');
            $table->string('slug')->unique(); // For URLs
            $table->text('excerpt')->nullable();
            
            // Content & Versions
            $table->longText('content'); // Markdown or HTML
            $table->unsignedInteger('version')->default(1);
            
            // Metadata
            $table->json('meta')->nullable(); // SEO, custom fields
            $table->string('status')->default('draft')->index(); // draft, published, archived
            
            // Relationships
            $table->unsignedBigInteger('author_id');
            $table->foreign('author_id')->references('id')->on('users')->cascadeOnDelete();
            
            $table->unsignedBigInteger('category_id')->nullable();
            // $table->foreign('category_id')->references('id')->on('categories');

            // Timestamps
            $table->timestamp('published_at')->nullable();
            $table->timestamps(); // create_at, updated_at
            $table->softDeletes(); // logical deletion
            
            // Indexes for performance
            $table->index(['status', 'published_at']);
        });
    }

    /**
     * Reverse the migrations.
     *
     * @return void
     */
    public function down()
    {
        Schema::dropIfExists('articles');
    }
}
