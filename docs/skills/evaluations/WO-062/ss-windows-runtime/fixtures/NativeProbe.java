import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

// Controlled WO-062 input: no network, files, listener or descendant process.
class NativeProbe {
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 2 || !args[1].matches("[0-9a-fA-F-]{36}")) {
            System.exit(64);
        }
        String mode = args[0];
        if (!mode.equals("failure") && !mode.equals("sleep")) {
            System.exit(64);
        }
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        out.println("WR_PROBE_RUN=" + args[1]);
        out.println("WR_PROBE_PID=" + ProcessHandle.current().pid());
        out.println("WR_PROBE_TEXT=caf\u00e9 \u00e9quipe");
        out.println("WR_PROBE_READY");
        if (mode.equals("failure")) {
            out.println("WR_PROBE_NATIVE_FAILURE");
            System.exit(23);
        }
        Thread.sleep(15000L);
        out.println("WR_PROBE_NATURAL_COMPLETION");
    }
}
