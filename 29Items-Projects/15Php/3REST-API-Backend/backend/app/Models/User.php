<?php

namespace App\Models;

use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

/**
 * @property int $id
 * @property string $name
 * @property string $email
 * @property string $role
 */
class User extends Authenticatable
{
    /** @use HasFactory<UserFactory> */
    use HasApiTokens, HasFactory, Notifiable;

    protected $fillable = [
        'name',
        'email',
        'password',
        'role',
    ];

    protected $hidden = [
        'password',
        'remember_token',
    ];

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
        ];
    }

    /** Products listed by this user (as a seller). */
    public function products(): HasMany
    {
        return $this->hasMany(Product::class);
    }

    /** Raw behavioral telemetry used by the recommendation engine. */
    public function events(): HasMany
    {
        return $this->hasMany(UserEvent::class);
    }

    /** Orders placed by this user (as a buyer). */
    public function orders(): HasMany
    {
        return $this->hasMany(Order::class);
    }

    /** Precomputed recommendations for this user. */
    public function recommendations(): HasMany
    {
        return $this->hasMany(Recommendation::class);
    }

    public function isAdmin(): bool
    {
        return $this->role === 'admin';
    }
}
