package com.staydesk.mockterminal;

public class MockTerminalMain {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;

        MockTerminalServer server = new MockTerminalServer(port);
        server.start();

        System.out.println("Mock TSI terminal listening on ws://localhost:" + port + "/tsi/v1/payment");
        System.out.println("Point the bridge agent's terminal-leg config at this address.");
        System.out.println();
        System.out.println("Magic amounts (sale/refund) for testing:");
        System.out.println("  $2.00  -> declined");
        System.out.println("  $9.99  -> no response at all (simulates a hung/unresponsive terminal)");
        System.out.println("  $0.01  -> malformed response (simulates a corrupt/unparseable message)");
        System.out.println("  anything else -> approved");
        System.out.println();
        System.out.println("Magic amounts for pre_auth:");
        System.out.println("  $2.00  -> declined");
        System.out.println("  $9.99  -> no response at all (simulates a hung/unresponsive terminal)");
        System.out.println("  $0.01  -> malformed response (simulates a corrupt/unparseable message)");
        System.out.println("  anything else -> approved, opens a pending pre-auth (not yet in the batch)");
        System.out.println();
        System.out.println("pre_auth_completion:");
        System.out.println("  reference_no not found among open pre-auths -> transaction_record_not_found");
        System.out.println("  otherwise -> approved, adds to the batch (amount sent, not the original hold amount)");
        System.out.println();
        System.out.println("Magic reference_no for void:");
        System.out.println("  contains \"DECLINE\" -> transaction_record_not_found");
        System.out.println("  anything else -> approved");
        System.out.println();
        System.out.println("Press Ctrl+C to stop.");
    }
}
