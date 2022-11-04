import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class test {
    public static void main(String[] args) {
        ConcurrentLinkedQueue<Integer> c = new ConcurrentLinkedQueue<>();
        c.add(1);
        System.out.println(c.remove(1));
        System.out.println(c.remove(5));
    }
}
