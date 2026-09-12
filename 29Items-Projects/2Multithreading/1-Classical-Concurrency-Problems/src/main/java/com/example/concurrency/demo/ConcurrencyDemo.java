package com.example.concurrency.demo;

import com.example.concurrency.barber.SleepingBarberShopDemo;
import com.example.concurrency.bathroom.UnisexBathroomDemo;
import com.example.concurrency.common.PhasedSimulationDemo;
import com.example.concurrency.deadlock.OrderedBankTransferDemo;
import com.example.concurrency.deadlock.TimedBankTransferDemo;
import com.example.concurrency.deadlock.UnsafeBankTransferDemo;
import com.example.concurrency.dining.ArbitratorDiningTableDemo;
import com.example.concurrency.dining.DeadlockDiningTableDemo;
import com.example.concurrency.dining.ResourceHierarchyDiningTableDemo;
import com.example.concurrency.h2o.WaterMoleculeBuilderDemo;
import com.example.concurrency.producerconsumer.BatchExchangerDemo;
import com.example.concurrency.producerconsumer.BoundedBufferDemo;
import com.example.concurrency.readerswriters.FairReadWriteLockDocumentDemo;
import com.example.concurrency.readerswriters.ReaderPriorityDocumentDemo;
import com.example.concurrency.readerswriters.UnfairReadWriteLockDocumentDemo;
import com.example.concurrency.readerswriters.WriterPriorityDocumentDemo;
import com.example.concurrency.santa.SantaWorkshopDemo;
import com.example.concurrency.smokers.CigaretteSmokersDemo;

/** Command selector for every standalone concurrency demonstration. */
public final class ConcurrencyDemo {
    private ConcurrencyDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "list".equals(args[0])) {
            printUsage();
            return;
        }

        switch (args[0]) {
            case "dining-hierarchy" -> ResourceHierarchyDiningTableDemo.main(args);
            case "dining-arbitrator" -> ArbitratorDiningTableDemo.main(args);
            case "dining-deadlock" -> DeadlockDiningTableDemo.main(args);
            case "sleeping-barber" -> SleepingBarberShopDemo.main(args);
            case "readers-reader-priority" -> ReaderPriorityDocumentDemo.main(args);
            case "readers-writer-priority" -> WriterPriorityDocumentDemo.main(args);
            case "readers-fair-lock" -> FairReadWriteLockDocumentDemo.main(args);
            case "readers-unfair-lock" -> UnfairReadWriteLockDocumentDemo.main(args);
            case "producer-consumer" -> BoundedBufferDemo.main(args);
            case "exchanger" -> BatchExchangerDemo.main(args);
            case "cigarette-smokers" -> CigaretteSmokersDemo.main(args);
            case "h2o" -> WaterMoleculeBuilderDemo.main(args);
            case "santa" -> SantaWorkshopDemo.main(args);
            case "unisex-bathroom" -> UnisexBathroomDemo.main(args);
            case "phaser" -> PhasedSimulationDemo.main(args);
            case "bank-deadlock" -> UnsafeBankTransferDemo.main(args);
            case "bank-ordered" -> OrderedBankTransferDemo.main(args);
            case "bank-try-lock" -> TimedBankTransferDemo.main(args);
            case "all-safe" -> runAllSafe();
            default -> throw new IllegalArgumentException(
                    "unknown demo '" + args[0] + "'; run with 'list' to see commands");
        }
    }

    private static void runAllSafe() throws Exception {
        ResourceHierarchyDiningTableDemo.main(new String[0]);
        ArbitratorDiningTableDemo.main(new String[0]);
        SleepingBarberShopDemo.main(new String[0]);
        ReaderPriorityDocumentDemo.main(new String[0]);
        WriterPriorityDocumentDemo.main(new String[0]);
        FairReadWriteLockDocumentDemo.main(new String[0]);
        UnfairReadWriteLockDocumentDemo.main(new String[0]);
        BoundedBufferDemo.main(new String[0]);
        BatchExchangerDemo.main(new String[0]);
        CigaretteSmokersDemo.main(new String[0]);
        WaterMoleculeBuilderDemo.main(new String[0]);
        SantaWorkshopDemo.main(new String[0]);
        UnisexBathroomDemo.main(new String[0]);
        PhasedSimulationDemo.main(new String[0]);
        OrderedBankTransferDemo.main(new String[0]);
        TimedBankTransferDemo.main(new String[0]);
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar <jar> <command>");
        System.out.println("Commands:");
        System.out.println("  dining-hierarchy          Resource-order prevention");
        System.out.println("  dining-arbitrator         N-1 semaphore arbitrator");
        System.out.println("  dining-deadlock           Deliberate deadlock detection");
        System.out.println("  sleeping-barber           Multiple barbers and waiting room");
        System.out.println("  readers-reader-priority   synchronized/wait/notify policy");
        System.out.println("  readers-writer-priority   Condition writer-priority policy");
        System.out.println("  readers-fair-lock         Fair ReadWriteLock policy");
        System.out.println("  readers-unfair-lock       Non-fair ReadWriteLock policy");
        System.out.println("  producer-consumer         Condition bounded buffer");
        System.out.println("  exchanger                 Two-party batch swap");
        System.out.println("  cigarette-smokers         Semaphore agent/smokers");
        System.out.println("  h2o                       CyclicBarrier molecule building");
        System.out.println("  santa                     Reindeer and elf groups");
        System.out.println("  unisex-bathroom           Fair exclusive-category admission");
        System.out.println("  phaser                    Dynamic phase coordination");
        System.out.println("  bank-deadlock             Deliberate transfer deadlock detection");
        System.out.println("  bank-ordered              Ordered-lock transfer prevention");
        System.out.println("  bank-try-lock             Timed tryLock rollback and retry");
        System.out.println("  all-safe                  Run every non-deadlocking demo");
    }
}
