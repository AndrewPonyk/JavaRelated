<?php

namespace App\Notifications;

use App\Models\Product;
use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

/**
 * Queued notification delivered over mail + database channels.
 * ShouldQueue keeps delivery off the request path.
 */
class ProductPurchasedNotification extends Notification implements ShouldQueue
{
    use Queueable;

    public function __construct(private readonly Product $product) {}

    /**
     * @return array<int, string>
     */
    public function via(object $notifiable): array
    {
        return ['mail', 'database'];
    }

    public function toMail(object $notifiable): MailMessage
    {
        return (new MailMessage)
            ->subject('Order confirmed')
            ->greeting("Thanks, {$notifiable->name}!")
            ->line("Your purchase of \"{$this->product->name}\" is confirmed.")
            ->action('View your orders', url('/orders'))
            ->line('We appreciate your business.');
    }

    /**
     * Stored in the notifications table for in-app display.
     *
     * @return array<string, mixed>
     */
    public function toArray(object $notifiable): array
    {
        return [
            'product_id' => $this->product->id,
            'product_name' => $this->product->name,
            'message' => "Your purchase of {$this->product->name} is confirmed.",
        ];
    }
}
