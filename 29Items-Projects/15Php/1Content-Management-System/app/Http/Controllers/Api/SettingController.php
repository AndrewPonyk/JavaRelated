<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class SettingController extends Controller
{
    /**
     * Get all settings.
     */
    public function index()
    {
        // Fetch all settings as key-value pairs
        $settings = \App\Models\Setting::all()->pluck('value', 'key');
        
        return response()->json([
            'data' => $settings
        ]);
    }

    /**
     * Update settings.
     */
    public function update(Request $request)
    {
        $validated = $request->validate([
            'settings' => 'required|array',
            'settings.*' => 'nullable|string'
        ]);

        foreach ($validated['settings'] as $key => $value) {
            \App\Models\Setting::updateOrCreate(
                ['key' => $key],
                ['value' => $value]
            );
        }

        return response()->json([
            'message' => 'Settings updated successfully',
            'data' => \App\Models\Setting::all()->pluck('value', 'key')
        ]);
    }
}
