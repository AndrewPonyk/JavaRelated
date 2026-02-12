<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>{{ config('app.name', 'InSight CMS') }}</title>
        <script>
            window.AppConfig = {
                name: "{{ config('app.name', 'InSight CMS') }}"
            };
        </script>
        @viteReactRefresh
        @vite(['resources/css/app.css', 'resources/js/app.jsx'])
    </head>
    <body class="bg-gray-100 text-gray-800 antialiased">
        <div id="root"></div>
    </body>
</html>
