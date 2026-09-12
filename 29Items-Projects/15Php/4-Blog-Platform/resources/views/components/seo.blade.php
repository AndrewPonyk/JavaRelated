@props(['seo'])

{{-- Renders the SEO payload produced by App\Services\Seo\SeoService. --}}
<title>{{ $seo['title'] ?? config('app.name') }}</title>
<meta name="description" content="{{ $seo['description'] ?? '' }}">
<meta name="robots" content="{{ $seo['robots'] ?? 'index,follow' }}">

@isset($seo['canonical'])
    <link rel="canonical" href="{{ $seo['canonical'] }}">
@endisset

{{-- Open Graph --}}
@foreach (($seo['og'] ?? []) as $property => $content)
    @if (filled($content))
        <meta property="{{ $property }}" content="{{ $content }}">
    @endif
@endforeach

{{-- Twitter cards --}}
@foreach (($seo['twitter'] ?? []) as $name => $content)
    @if (filled($content))
        <meta name="{{ $name }}" content="{{ $content }}">
    @endif
@endforeach

{{-- JSON-LD structured data.
     JSON_HEX_TAG|JSON_HEX_AMP escape <, > and & so a post title containing
     "</script>" cannot break out of this inline <script> block (XSS). --}}
@isset($seo['jsonLd'])
    <script type="application/ld+json">
        {!! json_encode($seo['jsonLd'], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) !!}
    </script>
@endisset
