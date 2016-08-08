package com.teamtreehouse.vending;

public class Main {

    public static void main(String[] args) {
	// write your code here
        Notifier notifier = new Notifier() {

            @Override
            public void onSale(Item item) {
                System.out.printf("Sold item %s for %s\n",
                        item.getName(),
                        item.getRetailPrice());
            }
        };
        // -create vend machine with 10x10 cells and max 10 items per item
        VendingMachine machine = new VendingMachine(notifier, 10, 10, 10);
        try {
            // -put some items in machine
            System.out.println("Restocking");
            machine.restock("A1", "Twinkies", 5, 30, 75);

            // -user put money inside machine
            System.out.println("Adding money");
            machine.addMoney(200);

            // -user vend some items
            System.out.println("Vending");
            machine.vend("A1");
            machine.vend("A1");

            System.out.println("Money available to creditor" + machine.getAvailableMoney());

            // -display how much money are in machine
            System.out.println("Money in machine: " + machine.getRunningSalesTotal());
        } catch (InvalidLocationException ile) {
            ile.printStackTrace();
        } catch (NotEnoughFundsException nefe) {
            nefe.printStackTrace();
        }
    }
}
