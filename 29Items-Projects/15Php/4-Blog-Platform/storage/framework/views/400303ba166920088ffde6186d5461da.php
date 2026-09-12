<?php echo '<'.'?xml version="1.0" encoding="UTF-8"?'.'>'; ?>

<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
    <channel>
        <title><?php echo e(config('app.name')); ?></title>
        <link><?php echo e(url('/')); ?></link>
        <description>Latest articles from <?php echo e(config('app.name')); ?></description>
        <atom:link href="<?php echo e(route('feed')); ?>" rel="self" type="application/rss+xml" />
        <?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if BLOCK]><![endif]--><?php endif; ?><?php $__currentLoopData = $posts; $__env->addLoop($__currentLoopData); foreach($__currentLoopData as $post): $__env->incrementLoopIndices(); $loop = $__env->getLastLoop(); ?>
        <item>
            <title><?php echo e($post->title); ?></title>
            <link><?php echo e(url("/blog/{$post->slug}")); ?></link>
            <guid><?php echo e(url("/blog/{$post->slug}")); ?></guid>
            <pubDate><?php echo e($post->published_at->toRssString()); ?></pubDate>
            <description><?php echo e($post->excerpt); ?></description>
        </item>
        <?php endforeach; $__env->popLoop(); $loop = $__env->getLastLoop(); ?><?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if ENDBLOCK]><![endif]--><?php endif; ?>
    </channel>
</rss>
<?php /**PATH /app/resources/views/feed/rss.blade.php ENDPATH**/ ?>