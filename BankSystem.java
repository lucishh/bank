// BankSystem.java
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.Base64;
import com.google.gson.*;

public class BankSystem {
    private static final String DATA_FILE = "bank_data.json";
    private static final String STAFF_PASSWORD = "admin123"; // change for production
    private static final Scanner scanner = new Scanner(System.in);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // ----------------- Data model -----------------
    static class BankAccount {
        String accountId;
        String ownerName;
        double balance;
        String cardNumber; // 16-digit card
        String pinHash;    // SHA-256 binary hash (256 chars of 0/1)

        BankAccount(String accountId, String ownerName, double balance) {
            this.accountId = accountId;
            this.ownerName = ownerName;
            this.balance = balance;
            this.cardNumber = null;
            this.pinHash = null;
        }
    }

    static class BankData {
        List<BankAccount> accounts = new ArrayList<>();
    }

    // ----------------- Main -----------------
    public static void main(String[] args) {
        BankData bank = loadData();

        while (true) {
            System.out.println("\n--- Simple Bank ---");
            System.out.println("1) Staff login");
            System.out.println("2) Customer login (card + PIN)");
            System.out.println("3) Exit");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1": staffMenu(bank); break;
                case "2": customerLogin(bank); break;
                case "3": saveData(bank); System.out.println("Goodbye."); return;
                default: System.out.println("Invalid option.");
            }
        }
    }

    // ----------------- Staff -----------------
    private static void staffMenu(BankData bank) {
        System.out.print("Enter staff password: ");
        if (!scanner.nextLine().equals(STAFF_PASSWORD)) {
            System.out.println("Wrong password."); return;
        }

        while (true) {
            System.out.println("\n--- Staff Menu ---");
            System.out.println("1) Create new account (no card)");
            System.out.println("2) Register card + PIN");
            System.out.println("3) List accounts");
            System.out.println("4) Back");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch(c){
                case "1": createAccount(bank); break;
                case "2": registerCard(bank); break;
                case "3": listAccounts(bank); break;
                case "4": saveData(bank); return;
                default: System.out.println("Invalid option.");
            }
        }
    }

    private static void createAccount(BankData bank) {
        System.out.print("Owner name: "); String owner = scanner.nextLine().trim();
        double initial = readDouble("Initial deposit: ");
        String id = generateAccountId(bank);
        bank.accounts.add(new BankAccount(id, owner, initial));
        saveData(bank);
        System.out.println("Created account ID: "+id);
    }

    private static void registerCard(BankData bank) {
        System.out.print("Enter account ID: "); String accId = scanner.nextLine().trim();
        Optional<BankAccount> maybe = bank.accounts.stream().filter(a->a.accountId.equals(accId)).findFirst();
        if(!maybe.isPresent()){ System.out.println("Account not found."); return; }
        BankAccount acc = maybe.get();

        System.out.print("16-digit card number: "); String card = scanner.nextLine().trim();
        if(!card.matches("\\d{16}")){ System.out.println("Card must be 16 digits."); return; }
        boolean used = bank.accounts.stream().anyMatch(a->card.equals(a.cardNumber));
        if(used){ System.out.println("Card already registered."); return; }

        System.out.print("4-digit PIN: "); String pin = scanner.nextLine().trim();
        if(!pin.matches("\\d{4}")){ System.out.println("PIN must be 4 digits."); return; }

        acc.cardNumber = card;
        acc.pinHash = hashPin(pin);
        saveData(bank);
        System.out.println("Card registered successfully.");
    }

    private static void listAccounts(BankData bank){
        System.out.println("\nAccounts:");
        for(BankAccount a: bank.accounts){
            String cardDisplay = (a.cardNumber==null)?"(no card)":"card:"+a.cardNumber;
            System.out.printf("ID=%s | Owner=%s | Balance=%.2f | %s%n",
                    a.accountId,a.ownerName,a.balance,cardDisplay);
        }
    }

    // ----------------- Customer -----------------
    private static void customerLogin(BankData bank){
        System.out.print("Enter 16-digit card: "); String card = scanner.nextLine().trim();
        System.out.print("Enter 4-digit PIN: "); String pin = scanner.nextLine().trim();
        Optional<BankAccount> maybe = bank.accounts.stream().filter(a->card.equals(a.cardNumber)).findFirst();
        if(!maybe.isPresent()){ System.out.println("Card not found."); return; }
        BankAccount acc = maybe.get();
        if(!hashPin(pin).equals(acc.pinHash)){ System.out.println("Incorrect PIN."); return; }

        System.out.println("Welcome, "+acc.ownerName+"!");
        customerMenu(bank, acc);
    }

    private static void customerMenu(BankData bank, BankAccount acc){
        while(true){
            System.out.println("\n--- Customer Menu ---");
            System.out.println("1) Check balance  2) Deposit  3) Withdraw  4) Logout");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch(c){
                case "1": System.out.printf("Balance: %.2f%n", acc.balance); break;
                case "2":
                    double dep = readDouble("Deposit amount: "); if(dep>0){ acc.balance+=dep; saveData(bank); System.out.println("Deposited."); } else { System.out.println("Amount must be positive."); } break;
                case "3":
                    double w = readDouble("Withdraw amount: "); 
                    if(w<=0){ System.out.println("Amount must be positive."); }
                    else if(w>acc.balance){ System.out.println("Insufficient funds."); }
                    else{ acc.balance-=w; saveData(bank); System.out.println("Withdrawn."); } break;
                case "4": System.out.println("Logged out."); return;
                default: System.out.println("Invalid option.");
            }
        }
    }

    // ----------------- Persistence -----------------
    private static BankData loadData(){
        try{
            Path p = Paths.get(DATA_FILE);
            if(!Files.exists(p)){ BankData empty = new BankData(); saveData(empty); return empty; }
            String json = new String(Files.readAllBytes(p));
            BankData data = gson.fromJson(json, BankData.class);
            return data==null?new BankData():data;
        }catch(Exception e){ System.out.println("Failed to load data: "+e.getMessage()); return new BankData();}
    }

    private static void saveData(BankData data){
        try(Writer writer = new FileWriter(DATA_FILE)){ gson.toJson(data, writer); } 
        catch(IOException e){ System.out.println("Failed to save data: "+e.getMessage()); }
    }

    // ----------------- Utilities -----------------
    private static String generateAccountId(BankData bank){
        int max=0;
        for(BankAccount a: bank.accounts){
            if(a.accountId!=null && a.accountId.startsWith("ACC")){
                try{ int n=Integer.parseInt(a.accountId.substring(3)); if(n>max)max=n; }catch(NumberFormatException ignore){}
            }
        }
        return "ACC"+(max+1);
    }

    private static String hashPin(String pin){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(pin.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for(byte b: bytes){
                sb.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ','0'));
            }
            return sb.toString(); // 256 bits binary string
        }catch(Exception e){ throw new RuntimeException(e); }
    }

    private static double readDouble(String prompt){
        while(true){
            System.out.print(prompt);
            try{ return Double.parseDouble(scanner.nextLine().trim()); }
            catch(NumberFormatException e){ System.out.println("Enter a valid number."); }
        }
    }
}
