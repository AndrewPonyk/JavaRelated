<?php

namespace Tests\Feature\Notifications;

use App\Models\Product;
use App\Models\User;
use App\Notifications\ProductPurchasedNotification;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class ProductPurchasedNotificationTest extends TestCase
{
    use RefreshDatabase;

    public function test_it_delivers_over_mail_and_database_channels(): void
    {
        $product = Product::factory()->create(['name' => 'Gizmo']);
        $user = User::factory()->create(['name' => 'Dana']);

        $notification = new ProductPurchasedNotification($product);

        $this->assertEqualsCanonicalizing(['mail', 'database'], $notification->via($user));
    }

    public function test_mail_payload_mentions_the_product(): void
    {
        $product = Product::factory()->create(['name' => 'Gizmo']);
        $user = User::factory()->create(['name' => 'Dana']);

        $mail = (new ProductPurchasedNotification($product))->toMail($user);

        $this->assertSame('Order confirmed', $mail->subject);
        $this->assertStringContainsString('Gizmo', implode(' ', $mail->introLines));
    }

    public function test_database_payload_carries_product_details(): void
    {
        $product = Product::factory()->create(['name' => 'Gizmo']);
        $user = User::factory()->create();

        $array = (new ProductPurchasedNotification($product))->toArray($user);

        $this->assertSame($product->id, $array['product_id']);
        $this->assertSame('Gizmo', $array['product_name']);
    }
}
