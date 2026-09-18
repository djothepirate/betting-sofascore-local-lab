import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Local ownership preflight only. No failure/sleep qualification, file or network writes. */
class OwnershipProbe {
    public static void main(String[] args) throws Exception {
        var self = ProcessHandle.current();
        var parent = self.parent().orElseThrow();
        System.out.println("OWNER_UUID=" + args[0]);
        System.out.println("OWNER_PID=" + self.pid());
        System.out.println("OWNER_PARENT_PID=" + parent.pid());
        System.out.println("OWNER_COMMAND=" + self.info().command().orElse("UNKNOWN"));
        System.out.println("OWNER_STARTED=" + self.info().startInstant().orElse(Instant.EPOCH));
        System.out.println("OWNER_JAVA_VERSION=" + Runtime.version());
        System.out.println("OWNER_READY");
        System.out.flush();
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (System.in.available() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        if (System.in.available() == 0) { System.out.println("OWNER_RELEASE_ABSENT"); return; }
        byte[] data = new byte[64];
        int count = System.in.read(data);
        if (!new String(data, 0, count, StandardCharsets.UTF_8).trim().equals("RELEASE"))
            throw new IllegalArgumentException("Unexpected release marker");
        System.out.println("OWNER_RELEASED");
    }
}
