<?php

use Illuminate\Support\Facades\Broadcast;

Broadcast::channel('App.Models.User.{id}', function (\, \) {
    return (int) \->id === (int) \;
});