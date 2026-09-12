<?php

declare(strict_types=1);

namespace Tests\Feature;

use App\Livewire\Auth\Login;
use App\Models\User;
use Illuminate\Foundation\Http\Middleware\VerifyCsrfToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Livewire\Livewire;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

class AuthTest extends TestCase
{
    use RefreshDatabase;

    #[Test]
    public function the_login_screen_is_reachable(): void
    {
        $this->get(route('login'))->assertOk()->assertSee('Sign in');
    }

    #[Test]
    public function a_user_can_authenticate_with_valid_credentials(): void
    {
        $user = User::factory()->create(['password' => Hash::make('secret-password')]);

        Livewire::test(Login::class)
            ->set('email', $user->email)
            ->set('password', 'secret-password')
            ->call('login')
            ->assertHasNoErrors()
            ->assertRedirect(route('admin.dashboard'));

        $this->assertAuthenticatedAs($user);
    }

    #[Test]
    public function authentication_fails_with_an_invalid_password(): void
    {
        $user = User::factory()->create(['password' => Hash::make('secret-password')]);

        Livewire::test(Login::class)
            ->set('email', $user->email)
            ->set('password', 'wrong-password')
            ->call('login')
            ->assertHasErrors('email');

        $this->assertGuest();
    }

    #[Test]
    public function login_requires_email_and_password(): void
    {
        Livewire::test(Login::class)
            ->set('email', '')
            ->set('password', '')
            ->call('login')
            ->assertHasErrors(['email' => 'required', 'password' => 'required']);
    }

    #[Test]
    public function login_is_throttled_after_too_many_failed_attempts(): void
    {
        $user = User::factory()->create(['password' => Hash::make('secret-password')]);

        $component = Livewire::test(Login::class)
            ->set('email', $user->email)
            ->set('password', 'wrong-password');

        // 5 attempts are allowed; the 6th is locked out by the rate limiter.
        foreach (range(1, 5) as $ignored) {
            $component->call('login');
        }

        $component->call('login')->assertHasErrors('email');

        $message = $component->errors()->first('email');
        $this->assertStringContainsString('Too many login attempts', $message);
    }

    #[Test]
    public function authenticated_users_can_log_out(): void
    {
        // Isolate logout logic from CSRF (the nav form supplies @csrf in the UI).
        $this->withoutMiddleware(VerifyCsrfToken::class);

        $this->actingAs(User::factory()->create());

        $this->post(route('logout'))->assertRedirect(route('home'));

        $this->assertGuest();
    }

    #[Test]
    public function logged_in_users_are_redirected_away_from_login(): void
    {
        $this->actingAs(User::factory()->create())
            ->get(route('login'))
            ->assertRedirect();
    }
}
