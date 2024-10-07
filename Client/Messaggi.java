package Client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Messaggi {

    private static int instanceCount = 0;
    private int id;
    private String testo;
    private LocalDate date;

    public Messaggi(int id, String testo, LocalDate date){
        this.id = ++instanceCount;
        this.testo = testo;
        this.date = date;
    }

    public int getId(){
        return id;
    }

    public String getTesto(){
        return testo;
    }


    //formattiamo la data per ottenere il formato gg/mm/aaaa - hh:mm:ss (es. 01/01/2021 - 12:00:00)
    public String getDate(){
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss");
        String formattedDateTime = currentDateTime.format(formatter);
        return formattedDateTime;
    }


    public String toString(){
        return  "ID: " +  id + "\nTesto: " + testo + "\nData: " + getDate();
    }


}//enc class
