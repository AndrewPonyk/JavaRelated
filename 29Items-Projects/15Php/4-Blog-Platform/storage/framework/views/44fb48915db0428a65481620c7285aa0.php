<!DOCTYPE html>
<html lang="<?php echo e(str_replace('_', '-', app()->getLocale())); ?>"
      x-data="{ dark: localStorage.getItem('theme') === 'dark' }"
      x-init="$watch('dark', v => localStorage.setItem('theme', v ? 'dark' : 'light'))"
      :class="{ 'dark': dark }">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    
    <script>
        if (localStorage.getItem('theme') === 'dark') {
            document.documentElement.classList.add('dark');
        }
    </script>

    
    <?php echo $__env->yieldPushContent('head'); ?>

    <link rel="preconnect" href="https://fonts.bunny.net">
    <link href="https://fonts.bunny.net/css?family=figtree:400,500,600&display=swap" rel="stylesheet">

    <?php echo app('Illuminate\Foundation\Vite')(['resources/css/app.css', 'resources/js/app.js']); ?>
    <?php echo \Livewire\Mechanisms\FrontendAssets\FrontendAssets::styles(); ?>

</head>
<body class="min-h-screen bg-white text-gray-900 antialiased dark:bg-gray-900 dark:text-gray-100">
    <header class="border-b border-gray-100 dark:border-gray-800">
        <nav class="mx-auto flex max-w-3xl items-center justify-between px-4 py-4">
            <a href="<?php echo e(route('home')); ?>" wire:navigate class="text-lg font-semibold"><?php echo e(config('app.name')); ?></a>

            <div class="flex items-center gap-4 text-sm">
                <?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if BLOCK]><![endif]--><?php endif; ?><?php if(auth()->guard()->check()): ?>
                    <a href="<?php echo e(route('admin.dashboard')); ?>" wire:navigate class="text-gray-600 hover:underline dark:text-gray-300">Dashboard</a>
                    <a href="<?php echo e(route('admin.posts')); ?>" wire:navigate class="text-gray-600 hover:underline dark:text-gray-300">Posts</a>
                    <a href="<?php echo e(route('admin.categories')); ?>" wire:navigate class="text-gray-600 hover:underline dark:text-gray-300">Categories</a>
                    <form method="POST" action="<?php echo e(route('logout')); ?>" class="inline">
                        <?php echo csrf_field(); ?>
                        <button type="submit" class="text-gray-600 hover:underline dark:text-gray-300">Log out</button>
                    </form>
                <?php endif; ?><?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if ENDBLOCK]><![endif]--><?php endif; ?>

                <button type="button" @click="dark = !dark"
                        class="rounded p-2 hover:bg-gray-100 dark:hover:bg-gray-800"
                        aria-label="Toggle dark mode">
                    <span x-show="!dark">🌙</span>
                    <span x-show="dark">☀️</span>
                </button>
            </div>
        </nav>
    </header>

    <main class="mx-auto max-w-3xl px-4 py-10">
        <?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if BLOCK]><![endif]--><?php endif; ?><?php if(session('status')): ?>
            <div class="mb-6 rounded-md bg-green-50 px-4 py-3 text-sm text-green-800 dark:bg-green-900/30 dark:text-green-300">
                <?php echo e(session('status')); ?>

            </div>
        <?php endif; ?><?php if(\Livewire\Mechanisms\ExtendBlade\ExtendBlade::isRenderingLivewireComponent()): ?><!--[if ENDBLOCK]><![endif]--><?php endif; ?>

        <?php echo e($slot); ?>

    </main>

    <footer class="mt-16 border-t border-gray-100 py-8 text-center text-sm text-gray-500 dark:border-gray-800">
        © <?php echo e(date('Y')); ?> <?php echo e(config('app.name')); ?>. Built with Laravel + Livewire.
    </footer>

    <?php echo \Livewire\Mechanisms\FrontendAssets\FrontendAssets::scripts(); ?>

</body>
</html>
<?php /**PATH /app/resources/views/layouts/app.blade.php ENDPATH**/ ?>