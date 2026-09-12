<div>
    <?php $__env->startPush('head'); ?>
        <?php if (isset($component)) { $__componentOriginal42da61123f891e63201d7be28f403427 = $component; } ?>
<?php if (isset($attributes)) { $__attributesOriginal42da61123f891e63201d7be28f403427 = $attributes; } ?>
<?php $component = Illuminate\View\AnonymousComponent::resolve(['view' => 'components.seo','data' => ['seo' => $seo]] + (isset($attributes) && $attributes instanceof Illuminate\View\ComponentAttributeBag ? $attributes->all() : [])); ?>
<?php $component->withName('seo'); ?>
<?php if ($component->shouldRender()): ?>
<?php $__env->startComponent($component->resolveView(), $component->data()); ?>
<?php if (isset($attributes) && $attributes instanceof Illuminate\View\ComponentAttributeBag): ?>
<?php $attributes = $attributes->except(\Illuminate\View\AnonymousComponent::ignoredParameterNames()); ?>
<?php endif; ?>
<?php $component->withAttributes(['seo' => \Illuminate\View\Compilers\BladeCompiler::sanitizeComponentAttribute($seo)]); ?>
<?php echo $__env->renderComponent(); ?>
<?php endif; ?>
<?php if (isset($__attributesOriginal42da61123f891e63201d7be28f403427)): ?>
<?php $attributes = $__attributesOriginal42da61123f891e63201d7be28f403427; ?>
<?php unset($__attributesOriginal42da61123f891e63201d7be28f403427); ?>
<?php endif; ?>
<?php if (isset($__componentOriginal42da61123f891e63201d7be28f403427)): ?>
<?php $component = $__componentOriginal42da61123f891e63201d7be28f403427; ?>
<?php unset($__componentOriginal42da61123f891e63201d7be28f403427); ?>
<?php endif; ?>
    <?php $__env->stopPush(); ?>

    <header class="mb-8">
        <h1 class="text-3xl font-bold tracking-tight">Articles</h1>
        <p class="mt-2 text-gray-500">Thoughts on engineering, product, and design.</p>
    </header>

    
    <div class="mb-8 flex flex-col gap-3 sm:flex-row">
        <input
            type="search"
            wire:model.live.debounce.400ms="search"
            placeholder="Search articles…"
            class="w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800"
            aria-label="Search articles"
        >
        <select
            wire:model.live="categorySlug"
            class="rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800"
            aria-label="Filter by category"
        >
            <option value="">All categories</option>
            <?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if BLOCK]><![endif]--><?php endif; ?><?php $__currentLoopData = $categories; $__env->addLoop($__currentLoopData); foreach($__currentLoopData as $category): $__env->incrementLoopIndices(); $loop = $__env->getLastLoop(); ?>
                <option value="<?php echo e($category->slug); ?>"><?php echo e($category->name); ?></option>
            <?php endforeach; $__env->popLoop(); $loop = $__env->getLastLoop(); ?><?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if ENDBLOCK]><![endif]--><?php endif; ?>
        </select>
    </div>

    
    <div wire:loading.class="opacity-50" class="space-y-8">
        <?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if BLOCK]><![endif]--><?php endif; ?><?php $__empty_1 = true; $__currentLoopData = $posts; $__env->addLoop($__currentLoopData); foreach($__currentLoopData as $post): $__env->incrementLoopIndices(); $loop = $__env->getLastLoop(); $__empty_1 = false; ?>
            <article wire:key="post-<?php echo e($post->id); ?>" class="group">
                <a href="<?php echo e(route('posts.show', $post)); ?>" wire:navigate class="block">
                    <div class="flex items-center gap-2 text-xs text-gray-500">
                        <?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if BLOCK]><![endif]--><?php endif; ?><?php if($post->category): ?>
                            <span class="rounded bg-gray-100 px-2 py-0.5 dark:bg-gray-800"><?php echo e($post->category->name); ?></span>
                        <?php endif; ?><?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if ENDBLOCK]><![endif]--><?php endif; ?>
                        <time datetime="<?php echo e($post->published_at->toDateString()); ?>">
                            <?php echo e($post->published_at->format('M j, Y')); ?>

                        </time>
                        <span>·</span>
                        <span><?php echo e($post->reading_time); ?> min read</span>
                    </div>
                    <h2 class="mt-2 text-xl font-semibold group-hover:underline"><?php echo e($post->title); ?></h2>
                    <p class="mt-1 text-gray-600 dark:text-gray-400"><?php echo e($post->excerpt); ?></p>
                </a>
            </article>
        <?php endforeach; $__env->popLoop(); $loop = $__env->getLastLoop(); if ($__empty_1): ?>
            <p class="py-12 text-center text-gray-500">No articles found. Try a different search.</p>
        <?php endif; ?><?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if ENDBLOCK]><![endif]--><?php endif; ?>
    </div>

    <div class="mt-10">
        <?php echo e($posts->links()); ?>

    </div>
</div>
<?php /**PATH /app/resources/views/livewire/posts/post-list.blade.php ENDPATH**/ ?>