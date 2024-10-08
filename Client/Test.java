package Client;

import java.time.LocalDate;

public class Test {
    public static void main(String args []) {
       Messaggi m1 = new Messaggi(1, "Ciao", LocalDate.now());
       Messaggi m2 = new Messaggi(2, "Come va?", LocalDate.now());
       Messaggi m3 = new Messaggi(3, "Tutto bene", LocalDate.now());
       System.out.println(m1);
         System.out.println(m2);
            System.out.println(m3);

    }
}
