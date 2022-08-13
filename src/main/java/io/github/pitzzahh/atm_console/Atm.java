package io.github.pitzzahh.atm_console;

import java.util.*;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.function.Predicate;
import static java.lang.System.exit;
import java.util.concurrent.TimeUnit;
import io.github.pitzzahh.atm.dao.AtmDAO;
import io.github.pitzzahh.atm.entity.Loan;
import com.github.pitzzahh.utilities.Print;
import io.github.pitzzahh.atm.entity.Client;
import io.github.pitzzahh.atm.entity.Message;
import io.github.pitzzahh.atm.service.AtmService;
import com.github.pitzzahh.utilities.SecurityUtil;
import io.github.pitzzahh.atm.entity.LockedAccount;
import com.github.pitzzahh.utilities.classes.Person;
import static com.github.pitzzahh.utilities.Print.print;
import com.github.pitzzahh.utilities.classes.enums.Role;
import static com.github.pitzzahh.utilities.Print.println;
import com.github.pitzzahh.utilities.classes.enums.Gender;
import com.github.pitzzahh.utilities.classes.enums.Status;
import io.github.pitzzahh.atm.database.DatabaseConnection;
import static com.github.pitzzahh.utilities.classes.TextColors.*;
import static com.github.pitzzahh.utilities.validation.Validator.*;
import static com.github.pitzzahh.utilities.classes.enums.Role.ADMIN;
import static com.github.pitzzahh.utilities.classes.enums.Role.CLIENT;
import static com.github.pitzzahh.utilities.classes.enums.Status.SUCCESS;
import static com.github.pitzzahh.utilities.classes.enums.Status.CANNOT_PERFORM_OPERATION;

/**
 * Automated Teller Machine.
 * Details are encrypted to avoid data leak.
 * @author pitzzahh
 */
public class Atm {

    private static AtmDAO atmDAO;
    private static AtmService service;
    private static DatabaseConnection databaseConnection;
    private static final Hashtable<String, Client> CLIENTS = new Hashtable<>();
    private static final Hashtable<String, List<Loan>> LOANS = new Hashtable<>();
    private static final Hashtable<String, Message> MESSAGES = new Hashtable<>();

    /**
     * main method.
     * @param args arguments.
     */
    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args)  {
        service = new AtmService(atmDAO, databaseConnection);
        service.setDataSource().accept(service
                        .connection
                        .setDriverClassName("org.postgresql.Driver")
                        .setUrl("jdbc:postgresql://localhost/atm")
                        .setUsername("postgres")
                        .setPassword("!Password123")
                        .getDataSource()
        );

        final var SCANNER = new Scanner(System.in);
        Machine.loadClients();
        Machine.loadClientLoans();
        for (;;)  {
            try {
                switch (home(SCANNER)) {
                    case ADMIN -> Machine.AdminAcc.admin(SCANNER);
                    case CLIENT -> Machine.ClientAcc.client(SCANNER);
                }
            } catch (RuntimeException | IllegalAccessException runtimeException) {
                println(RED_BOLD_BRIGHT +  runtimeException.getMessage() + RESET);
                print(YELLOW_BOLD_BRIGHT + "LOADING");
                dotLoading();
            }
        }
    }

    /**
     * The automated teller machine that handles most of the process.
     */
    protected static class Machine {

        private static final String LOCKED_ACCOUNT_MESSAGE = "\nACCOUNT LOCKED\nPLEASE CONTACT THE ADMINISTRATOR TO VERIFY YOUR IDENTITY AND UNLOCK YOUR ACCOUNT\n";
        private static final String $adm = "QGRtMW4xJHRyNHQwcg==";
        private static String $an;
        private static final int DEPOSIT = 1;
        private static final int CHECK_BALANCE = 2;
        private static final int WITHDRAW = 3;
        private static final int LOAN = 4;
        private static final int APPROVE = 5;
        private static final int DECLINE = 6;

        /**
         * Searches the {@code Hashtable<String, Client>}, checks if the account number exists.
         * @param n the account number to search.
         * @return {@code true} if the account number exists.
         */
        private static boolean searchAccount(String n) {
            return CLIENTS
                    .keySet()
                    .stream()
                    .anyMatch(number -> number.equals(n));
        }

        /**
         * Searches the {@code searchLockedAccount()} method, checks if the account number exists.
         * @param n the account number to search.
         * @return {@code true} if the account number exists.
         */
        private static boolean searchLockedAccount(String n) {
            return AdminAcc.getAllLockedAccounts()
                    .stream()
                    .map(LockedAccount::accountNumber)
                    .anyMatch(an -> an.equals(n));
        }

        /**
         * Loads all the clients from the database.
         */
        private static void loadClients() {
            CLIENTS.putAll(service.getAllClients().get());
        }

        /**
         * Loads all the loans from the database.
         */
        private static void loadClientLoans() {
            LOANS.putAll(service.getAllLoans().get());
        }

        /**
         * class for Admin account.
         */
        protected static class AdminAcc {

            /**
             * called then an admin is logged in.
             * @param scanner the scanner needed for keyboard input.
             * @throws IllegalArgumentException if any of the input is not valid from the detail(scanner) method.
             * @throws IllegalStateException if there are no clients available.
             */
            private static void admin(Scanner scanner) throws IllegalArgumentException, IllegalStateException {
                var choice = "";
                do {
                    try {
                        println(PURPLE_BOLD_BRIGHT + "\n" +
                                "╔═╗╔╦╗ ╔╦╗ ╦ ╔╗╔\n" +
                                "╠═╣ ║║ ║║║ ║ ║║║\n" +
                                "╩ ╩ ╩╝ ╩ ╩ ╩ ╝╚╝\n" + RESET
                        );
                        println(RED_BOLD  + ": " + BLUE_BOLD_BRIGHT   + "1" + RED_BOLD + " : " + BLUE_BOLD_BRIGHT   + "ADD CLIENT");
                        println(RED_BOLD  + ": " + YELLOW_BOLD_BRIGHT + "2" + RED_BOLD + " : " + YELLOW_BOLD_BRIGHT + "REMOVE CLIENT");
                        println(RED_BOLD  + ": " + GREEN_BOLD_BRIGHT  + "3" + RED_BOLD + " : " + GREEN_BOLD_BRIGHT  + "VIEW CLIENTS");
                        println(RED_BOLD  + ": " + PURPLE_BOLD_BRIGHT + "4" + RED_BOLD + " : " + PURPLE_BOLD_BRIGHT + "MANAGE LOCKED ACCOUNTS");
                        println(RED_BOLD  + ": " + CYAN_BOLD_BRIGHT   + "5" + RED_BOLD + " : " + CYAN_BOLD_BRIGHT   + "MANAGE ACCOUNT LOANS");
                        println(RED_BOLD  + ": " + RED                + "6" + RED_BOLD + " : " + RED + "LOGOUT");
                        print(PURPLE_BOLD + ">>>: " + RESET);
                        choice = scanner.nextLine().trim();
                        switch (choice) {
                            case "1" -> {
                                var status = createClient(scanner);
                                println(status == SUCCESS ? BLUE_BOLD_BRIGHT + "\nCLIENT ADDED SUCCESSFULLY\n" : RED_BOLD_BRIGHT + "\nERROR ADDING CLIENT\n" + RESET);
                            }
                            case "2" -> {
                                var status = removeClient(scanner, getAllLockedAccounts());
                                println(status == SUCCESS ? BLUE_BOLD_BRIGHT + "\nCLIENT REMOVED SUCCESSFULLY\n" : RED_BOLD_BRIGHT + "\nERROR REMOVING CLIENT\n" + RESET);
                            }
                            case "3" -> viewAllClients();
                            case "4" -> manageLockedAccounts(scanner);
                            case "5" -> manageAccountLoans(scanner);
                            case "6" -> {
                                print(RED_BOLD_BRIGHT + "LOGGING OUT");
                                dotLoading();
                            }
                            default -> throw new IllegalStateException(String.format("\nINVALID INPUT: %s\n", choice));
                        }
                        CLIENTS.clear();
                        loadClients();
                        LOANS.clear();
                        loadClientLoans();
                        if (!choice.equals("6")) pause();
                        if (!choice.equals("3")) println();
                    } catch (RuntimeException runtimeException) {
                        println(RED_BOLD_BRIGHT +  runtimeException.getMessage() + RESET);
                        print(YELLOW_BOLD_BRIGHT + "LOADING");
                        dotLoading();
                    }
                } while (!choice.equals("6"));
            }

            /**
             * Creates a new {@code Client} and adds it to the {@code Hashtable<String, Client>}
             * @param scanner the scanner needed for keyboard input.
             * @throws IllegalArgumentException if any of the input is not valid from the detail(scanner) method.
             */
            private static Status createClient(Scanner scanner) throws IllegalArgumentException {
                println(PURPLE_BOLD_BRIGHT +
                        "╔═╗╔╦╗╔╦╗" + YELLOW_BOLD + "  ╔═╗╦  ╦╔═╗╔╗╔╔╦╗\n" + PURPLE_BOLD_BRIGHT +
                        "╠═╣ ║║ ║║" + YELLOW_BOLD + "  ║  ║  ║║╣ ║║║ ║ \n" + PURPLE_BOLD_BRIGHT +
                        "╩ ╩═╩╝═╩╝" + YELLOW_BOLD + "  ╚═╝╩═╝╩╚═╝╝╚╝ ╩ \n"
                );
                var client = details(scanner);
                return service.saveClient().apply(
                        new Client(
                                client.accountNumber(),
                                client.pin(),
                                new Person(
                                        client.details().getFirstName(),
                                        client.details().getLastName(),
                                        client.details().getGender(),
                                        client.details().getAddress(),
                                        client.details().getBirthDate()
                                ),
                                client.savings(),
                                client.isLocked()
                        )
                );
            }

            /**
             * method for asking for user details.
             * @param scanner the scanner needed for keyboard input.
             * @return a {@code Client} object.
             * @throws IllegalArgumentException if any of the input is not valid.
             */
            protected static Client details(Scanner scanner) throws IllegalArgumentException {

                var an = "";
                var pin = "";
                var firstName = "";
                var lastName = "";
                var gender = "";
                var address = "";
                var birthDate = "";
                var doesExist = false;
                do {
                    try {
                        print(BLUE_BOLD_BRIGHT + "Enter account number: ");
                        an = scanner.nextLine().trim();
                        checkAccountNumberInput(an);
                        doesExist = searchAccount(an);
                        if (doesExist) println(RED_BOLD_BRIGHT + String.format("\nCLIENT WITH ACCOUNT NUMBER : %s ALREADY EXISTS\n", an) + RESET);
                    } catch (RuntimeException runtimeException) {
                        println(RED_BOLD_BRIGHT +  runtimeException.getMessage() + RESET);
                    }
                } while (isIdValid().negate().test(an) || (an.length() < 9 || an.length() > 9) || doesExist);

                do {
                    print(PURPLE_BOLD_BRIGHT + "Enter account pin   : ");
                    pin = scanner.nextLine().trim();
                    if (isWholeNumber().negate().test(pin) || (pin.length() < 6 || pin.length() > 6)) println(RED_BOLD_BRIGHT + "\nPIN SHOULD BE 6 DIGITS\n" + RESET);
                } while (isWholeNumber().negate().test(pin) || (pin.length() < 6 || pin.length() > 6));

                print(YELLOW_BOLD_BRIGHT + "Enter client first name: ");
                firstName = scanner.nextLine().toUpperCase().trim();

                print(YELLOW_BOLD_BRIGHT + "Enter client last name : ");
                lastName = scanner.nextLine().toUpperCase().trim();

                do {
                    print(GREEN_BOLD_BRIGHT + "Enter client gender    : ");
                    gender = scanner.nextLine().toUpperCase().trim();
                    if (isGenderValid().negate().test(gender)) println(RED_BOLD_BRIGHT + "\nUnknown Gender, please select from the list: " + Arrays.toString(Arrays.stream(Gender.values()).map(Gender::name).toArray()) + "\n" + RESET);
                } while (isGenderValid().negate().test(gender));

                print(BLUE_BOLD_BRIGHT + "Enter client address   : ");
                address = scanner.nextLine().toUpperCase().trim();
                List<String> b = new ArrayList<>();
                var year = 2000;
                var month = 1;
                var day = 1;
                do {
                    print(PURPLE_BOLD_BRIGHT + "Enter client birthdate : ");
                    birthDate = scanner.nextLine().trim();
                    if (isWholeNumber().or(isDecimalNumber()).or(isString()).test(birthDate)) throw new IllegalArgumentException("\nINVALID BIRTHDATE FORMAT, VALID FORMAT: (YYYY-MM-dd)\n");
                    b = Arrays.stream(birthDate.split("-")).toList();
                    year = Integer.parseInt(b.get(0));
                    month = Integer.parseInt(b.get(1));
                    day = Integer.parseInt(b.get(2));
                    if (year < 1850 || year > 2029) println(RED_BOLD_BRIGHT + String.format("\nINVALID YEAR: %s\n", String.valueOf(year)) + RESET);
                    if (month <= 0 || month > 12) println(RED_BOLD_BRIGHT + String.format("\nINVALID MONTH: %s\n", String.valueOf(month)) + RESET);
                    if (day <= 0 || (day > 31 && !LocalDate.of(year, month, day).isLeapYear())) println(RED_BOLD_BRIGHT + String.format("\nINVALID DAY: %s\n", String.valueOf(day)) + RESET);
                } while (isBirthDateValid().negate().test(birthDate));
                return new Client(
                        an,
                        pin,
                        new Person(
                                firstName,
                                lastName,
                                Gender.valueOf(gender),
                                address,
                                LocalDate.of(year, month, day)
                        ),
                        5000.00,
                        false
                );
            }

            /**
             * Method for removing clients, used by admin.
             * @param scanner the scanner needed for keyboard input.
             * @return a {@code Status} of removal.
             * @throws IllegalStateException if there are no clients available.
             */
            protected static Status removeClient(Scanner scanner, List<LockedAccount> lockedAccounts) throws IllegalStateException {
                checkIfThereAreClientsAvailable();
                println(RED_BOLD_BRIGHT +
                        "╦═╗╔═╗╔╦╗╔═╗╦  ╦╔═╗" + YELLOW_BOLD + "  ╔═╗╦  ╦╔═╗╔╗╔╔╦╗\n" + RED_BOLD_BRIGHT +
                        "╠╦╝║╣ ║║║║ ║╚╗╔╝║╣ " + YELLOW_BOLD + "  ║  ║  ║║╣ ║║║ ║ \n" + RED_BOLD_BRIGHT +
                        "╩╚═╚═╝╩ ╩╚═╝ ╚╝ ╚═╝" + YELLOW_BOLD + "  ╚═╝╩═╝╩╚═╝╝╚╝ ╩ \n" + RESET
                );
                if (CLIENTS.size() == 1) $an = CLIENTS.entrySet().stream().findAny().get().getValue().accountNumber();
                else if (lockedAccounts.size() == 1) $an = CLIENTS.get(lockedAccounts.get(0).accountNumber()).accountNumber();
                else {
                    println(RED_BOLD_BRIGHT + ":ENTER ACCOUNT NUMBER TO REMOVE:");
                    print(PURPLE_BOLD + ">>>: " + RESET);
                    $an = scanner.nextLine().trim();
                    checkAccountNumberInput($an);
                    if (!searchAccount($an)) throw new IllegalStateException(String.format("\nACCOUNT: %s DOES NOT EXIST\n", $an));
                }
                CLIENTS.remove($an);
                return service.removeClientByAccountNumber().apply($an);
            }

            /**
             * Prints all the clients details.
             * @throws IllegalStateException if there are no clients available.
             */
            protected static void viewAllClients() throws IllegalStateException {
                checkIfThereAreClientsAvailable();
                CLIENTS.entrySet()
                        .stream()
                        .map(Map.Entry::getValue)
                        .forEach(Print::println);
            }

            /**
             * Manages locked accounts.
             * @param scanner the scanner needed for keyboard input.
             */
            private static void manageLockedAccounts(Scanner scanner) throws IllegalStateException {
                var LOCKED_ACCOUNTS = getAllLockedAccounts();
                if (LOCKED_ACCOUNTS.isEmpty()) throw new IllegalStateException("\nTHERE ARE NO LOCKED ACCOUNTS\n");
                String choice = "";
                do {
                    println(YELLOW_BOLD_BRIGHT +
                            "╔╦╗╔═╗╔╗╔╔═╗╔═╗╔═╗  ╦  ╔═╗╔═╗╦╔═╔═╗╔╦╗  ╔═╗╔═╗╔═╗╔═╗╦ ╦╔╗╔╔╦╗╔═╗\n" +
                            "║║║╠═╣║║║╠═╣║ ╦║╣   ║  ║ ║║  ╠╩╗║╣  ║║  ╠═╣║  ║  ║ ║║ ║║║║ ║ ╚═╗\n" +
                            "╩ ╩╩ ╩╝╚╝╩ ╩╚═╝╚═╝  ╩═╝╚═╝╚═╝╩ ╩╚═╝═╩╝  ╩ ╩╚═╝╚═╝╚═╝╚═╝╝╚╝ ╩ ╚═╝\n"
                    );
                    println(RED_BOLD_BRIGHT + "LOCKED " + (LOCKED_ACCOUNTS.size() > 1 ? "ACCOUNTS" : "ACCOUNT") +"\n");
                    LOCKED_ACCOUNTS.forEach(Print::println);
                    println(PURPLE_BOLD + ":" + BLUE_BOLD_BRIGHT + " 1 " + PURPLE_BOLD_BRIGHT + ": " + BLUE_BOLD_BRIGHT + "REOPEN LOCKED ACCOUNT");
                    println(PURPLE_BOLD + ":" + RED_BOLD_BRIGHT + " 2 " + PURPLE_BOLD_BRIGHT + ": " + RED_BOLD_BRIGHT + "DELETE LOCKED ACCOUNT");
                    println(PURPLE_BOLD + ":" + GREEN_BOLD_BRIGHT + " 3 " + PURPLE_BOLD_BRIGHT + ": " + GREEN_BOLD_BRIGHT + "BACK");
                    print(YELLOW_BOLD + ">>>: " + RESET);
                    choice = scanner.nextLine().trim();
                    switch (choice) {
                        case "1" -> {
                            println(BLUE_BOLD_BRIGHT + "REOPEN ACCOUNT" + RESET);
                            var status = reopenAccount(scanner, LOCKED_ACCOUNTS);
                            println(status == SUCCESS ? BLUE_BOLD_BRIGHT + "\nACCOUNT REOPENED SUCCESSFULLY\n" :
                                            RED_BOLD_BRIGHT + "\nERROR REOPENING ACCOUNT\n" + RESET
                            );
                        }
                        case "2" -> {
                            println(YELLOW_BOLD_BRIGHT + "REMOVE ACCOUNT" + RESET);
                            var status = removeClient(scanner, LOCKED_ACCOUNTS);
                            println(status == SUCCESS ? BLUE_BOLD_BRIGHT + "\nACCOUNT REMOVED SUCCESSFULLY\n" :
                                            RED_BOLD_BRIGHT + "\nERROR REMOVING ACCOUNT\n" + RESET
                            );
                        }
                        case "3" -> {
                            print(RED_BOLD_BRIGHT + "PLEASE WAIT");
                            dotLoading();
                        }
                        default -> throw new IllegalStateException(String.format("\nINVALID INPUT: %s\n", choice));
                    }
                    CLIENTS.clear();
                    LOCKED_ACCOUNTS.clear();
                    loadClients();
                    LOCKED_ACCOUNTS = getAllLockedAccounts();
                    if (LOCKED_ACCOUNTS.isEmpty()) break;
                } while (!choice.equals("3"));
            }

            /**
             *  Manages account loans.
             * @param scanner the scanner needed for keyboard input.
             */
            private static void manageAccountLoans(Scanner scanner) {
                var LOAN_REQUESTS = getAllLoanRequests();
                if (LOAN_REQUESTS.isEmpty()) throw new IllegalStateException("\nTHERE ARE NO LOAN REQUESTS AVAILABLE\n");
                String choice = "";
                do {
                    println(YELLOW_BOLD_BRIGHT +
                            "╔╦╗╔═╗╔╗╔╔═╗╔═╗╔═╗  ╔═╗╔═╗╔═╗╔═╗╦ ╦╔╗╔╔╦╗  ╦  ╔═╗╔═╗╔╗╔╔═╗\n" +
                            "║║║╠═╣║║║╠═╣║ ╦║╣   ╠═╣║  ║  ║ ║║ ║║║║ ║   ║  ║ ║╠═╣║║║╚═╗\n" +
                            "╩ ╩╩ ╩╝╚╝╩ ╩╚═╝╚═╝  ╩ ╩╚═╝╚═╝╚═╝╚═╝╝╚╝ ╩   ╩═╝╚═╝╩ ╩╝╚╝╚═╝\n");
                    println(RED_BOLD_BRIGHT + (LOAN_REQUESTS.size() > 1 ? "LIST OF LOANS" : "LOAN") + "\n");
                    LOAN_REQUESTS.stream().forEach(Print::println);
                    println(PURPLE_BOLD + ":" + BLUE_BOLD_BRIGHT   + " 1 " + PURPLE_BOLD_BRIGHT + ": " + BLUE_BOLD_BRIGHT   + "APPROVE LOAN");
                    println(PURPLE_BOLD + ":" + YELLOW_BOLD_BRIGHT + " 2 " + PURPLE_BOLD_BRIGHT + ": " + YELLOW_BOLD_BRIGHT + "DECLINE LOAN");
                    println(PURPLE_BOLD + ":" + RED_BOLD_BRIGHT    + " 3 " + PURPLE_BOLD_BRIGHT + ": " + RED_BOLD_BRIGHT    + "REMOVE LOAN");
                    println(PURPLE_BOLD + ":" + GREEN_BOLD_BRIGHT  + " 4 " + PURPLE_BOLD_BRIGHT + ": " + GREEN_BOLD_BRIGHT  + "BACK");
                    print(YELLOW_BOLD + ">>>: " + RESET);
                    choice = scanner.nextLine().trim();
                    switch (choice) {
                        case "1" -> {
                            println(BLUE_BOLD_BRIGHT + "APPROVE LOAN" + RESET);
                            var status = process(scanner, LOAN_REQUESTS, APPROVE);
                            println(status == SUCCESS ? BLUE_BOLD_BRIGHT + "\nLOAN APPROVED SUCCESSFULLY\n" :
                                    RED_BOLD_BRIGHT + "\nERROR APPROVING LOAN REQUEST\n" + RESET
                            );
                        }
                        case "2" -> {
                            println(YELLOW_BOLD_BRIGHT + "DECLINE LOAN" + RESET);
                            var status = process(scanner, LOAN_REQUESTS, DECLINE);
                            println(status == SUCCESS ? BLUE_BOLD_BRIGHT + "\nLOAN DECLINED SUCCESSFULLY\n" :
                                    RED_BOLD_BRIGHT + "\nERROR DECLINING LOAN REQUEST\n" + RESET
                            );
                        }
                        case "3" -> {
                            println(RED_BOLD_BRIGHT + "REMOVE LOAN" + RESET);
                            var status = removeLoan(scanner, LOAN_REQUESTS);
                            println(status == SUCCESS ? BLUE_BOLD_BRIGHT + "\nACCOUNT REMOVED SUCCESSFULLY\n" :
                                    RED_BOLD_BRIGHT + "\nERROR REMOVING LOAN REQUEST\n" + RESET
                            );
                        }
                        case "4" -> {
                            print(RED_BOLD_BRIGHT + "PLEASE WAIT");
                            dotLoading();
                        }
                        default -> throw new IllegalStateException(String.format("\nINVALID INPUT: %s\n", choice));
                    }
                    CLIENTS.clear();
                    LOANS.clear();
                    LOAN_REQUESTS.clear();
                    loadClients();
                    loadClientLoans();
                    LOAN_REQUESTS = getAllLoanRequests();
                    if (LOAN_REQUESTS.isEmpty()) break;
                } while (!choice.equals("4"));
            }

            /**
             * Approves a loan
             * @param scanner the scanner needed for keyboard input.
             * @param allLoans the {@code List<Loan>}.
             * @param transaction the transaction to be processed wether {@link Machine#APPROVE} or {@link Machine#DECLINE}.
             * @return a {@code Status}
             */
            private static Status process(Scanner scanner, List<Loan> allLoans, int transaction) {
                var loan = new Loan();
                var client = new Client();
                if (allLoans.size() == 1 && transaction == APPROVE) {
                    loan = allLoans.get(0);
                    client = CLIENTS.get(loan.accountNumber());
                    return service.approveLoan().apply(mapLoan(loan), client);
                }
                if (allLoans.size() == 1 && transaction == DECLINE) return service.declineLoan().apply(mapLoan(allLoans.get(0)));
                else {
                    println(CYAN_BOLD_BRIGHT + String.format("%s %s %s LOAN REQUEST" + RESET,
                            (transaction == APPROVE ? BLUE_BOLD : RED_BOLD),
                            (transaction == APPROVE ? "APPROVE" : "DECLINE"),
                            CYAN_BOLD_BRIGHT)
                    );
                    println(YELLOW_BOLD_BRIGHT + ":ENTER LOAN NUMBER AND ACCOUNT NUMBER TO SEPARATED BY SPACE:");
                    println(GREEN_BOLD + "example: " + YELLOW_BOLD_BRIGHT + CYAN_BACKGROUND + "1" + RESET + " " + YELLOW_BOLD_BRIGHT + CYAN_BACKGROUND + "200263444" + RESET);
                    print(PURPLE_BOLD + ">>>: " + RESET);
                    final var $s = scanner.nextLine().trim().split("\\s");
                    if (Validator.isValidFormat().negate().test($s[0] + " " + $s[1])) throw new IllegalStateException("\nINVALID FORMAT\nPLEASE FOLLOW THE EXAMPLE\n");
                    else if (isWholeNumber().negate().test($s[0])) throw new IllegalStateException("\nLOAN NUMBER SHOULD BE A NUMBER\n");
                    else if (isWholeNumber().negate().test($s[1])) throw new IllegalStateException("\nACCOUNT NUMBER SHOULD BE A NUMBER\n");

                    loan = allLoans
                            .stream()
                            .filter(e -> e.loanNumber() == Integer.parseInt($s[0]) && e.accountNumber().equals($s[1]))
                            .findAny()
                            .orElseThrow(() -> new IllegalStateException(String.format("\nLOAN %s WITH ACCOUNT NUMBER: %s DOES NOT EXIST\n", $s[0], $s[1])));

                    client = CLIENTS.get(loan.accountNumber());
                }

                if (transaction == APPROVE) return service.approveLoan().apply(mapLoan(loan), client);
                else return service.declineLoan().apply(mapLoan(loan));
            }

            // TODO: implement
            private static Status removeLoan(Scanner scanner, List<Loan> allLoans) {
                return CANNOT_PERFORM_OPERATION;
            }

            private static Loan mapLoan(Loan loan) {
                return new Loan(
                        loan.loanNumber(),
                        loan.accountNumber(),
                        loan.amount(),
                        false
                );
            }

            /**
             * Reopens a locked account.
             * @param scanner the scanner needed for keyboard input.
             * @return a {@code Status} of the operation.
             */
            private static Status reopenAccount(Scanner scanner, List<LockedAccount> lockedAccounts) {
                if (lockedAccounts.size() == 1) $an = lockedAccounts.stream().findAny().get().accountNumber();
                else {
                    println(YELLOW_BOLD_BRIGHT + ":ENTER ACCOUNT NUMBER TO REOPEN:");
                    print(PURPLE_BOLD + ">>>: " + RESET);
                    $an = scanner.nextLine().trim();
                    checkAccountNumberInput($an);
                    if (!searchLockedAccount($an)) throw new IllegalStateException(String.format("\nACCOUNT: %s DOES NOT EXIST\n", $an));
                }
                return service.updateClientAccountStatusByAccountNumber().apply($an, false);
            }

            /**
             * Gets all the list of locked accounts {@code Client} object from {@link Atm#CLIENTS}, and creating {@code LockedAccount} objects.
             * @return a {@code List<LockedAccounts>}
             */
            protected static List<LockedAccount> getAllLockedAccounts() {
                return CLIENTS.entrySet()
                        .stream()
                        .map(Map.Entry::getValue)
                        .filter(Client::isLocked)
                        .map(client -> {
                            return new LockedAccount(
                                    client.accountNumber(),
                                    client.details().getFirstName().concat(" " + client.details().getLastName()),
                                    client.details().getGender()
                            );
                        })
                        .collect(Collectors.toList());
            }

            /**
             * Gets all the values from the {@link Atm#LOANS}, that contains all the loan requests.
             * @return a {@code List<Loan>}
             */
            private static List<Loan> getAllLoanRequests() {
                return LOANS.entrySet()
                        .stream()
                        .map(Map.Entry::getValue)
                        .flatMap(Collection::stream)
                        .filter(l -> !l.isDeclined() && l.pending())
                        .collect(Collectors.toList());
            }

            /**
             * Checks if there are no clients available.
             * @throws IllegalStateException if there are no clients available.
             */
            private static void checkIfThereAreClientsAvailable() throws IllegalStateException {
                if (CLIENTS.isEmpty()) throw new IllegalStateException("\nTHERE ARE NO CLIENTS AVAILABLE\n");
            }

        }

        /**
         * class for Client account.
         */
        protected static class ClientAcc {

            /**
             * Called when a client is logged in.
             */
            private static void client(Scanner scanner) {
                var choice = "";
                do {
                    try {
                        println(BLUE_BOLD + ": " + BLUE_BOLD_BRIGHT   + "1" + BLUE_BOLD + " : " + BLUE_BOLD_BRIGHT   + "DEPOSIT");
                        println(BLUE_BOLD + ": " + PURPLE_BOLD_BRIGHT + "2" + BLUE_BOLD + " : " + PURPLE_BOLD_BRIGHT + "CHECK BALANCE");
                        println(BLUE_BOLD + ": " + YELLOW_BOLD_BRIGHT + "3" + BLUE_BOLD + " : " + YELLOW_BOLD_BRIGHT + "WITHDRAW");
                        println(BLUE_BOLD + ": " + RED                + "4" + BLUE_BOLD + " : " + RED                + "LOAN");
                        println(BLUE_BOLD + ": " + CYAN_BOLD_BRIGHT   + "5" + BLUE_BOLD + " : " + CYAN_BOLD_BRIGHT   + "MESSAGES");
                        println(BLUE_BOLD + ": " + RED_BOLD_BRIGHT    + "6" + BLUE_BOLD + " : " + RED_BOLD_BRIGHT    + "LOGOUT");
                        print(PURPLE_BOLD + ">>>: " + RESET);
                        choice = scanner.nextLine().trim();
                        switch (choice) {
                            case "1" -> process(scanner, DEPOSIT);
                            case "2" -> process(scanner, CHECK_BALANCE);
                            case "3" -> process(scanner, WITHDRAW);
                            case "4" -> process(scanner, LOAN);
                            case "5" -> viewMessages(scanner);
                            case "6" -> {
                                print(RED_BOLD_BRIGHT + "LOGGING OUT");
                                dotLoading();
                            }
                            default -> throw new IllegalStateException(String.format("\nINVALID INPUT: %s\n", choice));
                        }
                    } catch (RuntimeException runtimeException) {
                        println(RED_BOLD_BRIGHT +  runtimeException.getMessage() + RESET);
                        print(YELLOW_BOLD_BRIGHT + "LOADING");
                        dotLoading();
                    }
                    CLIENTS.clear();
                    loadClients();
                    if (!choice.equals("6")) pause();
                    if (searchLockedAccount($an)) break;
                } while (!choice.equals("6"));
                loadClientLoans();
            }

            /**
             * Used for making client transactions.
             * Used to deposit, withdraw and loan cash.
             * @param scanner a {@code Scanner} object needed for user input.
             * @param transaction the transaction to be processed.
             */
            private static void process(Scanner scanner, int transaction) {
                var client = CLIENTS.get($an);
                var isLoggedIn = false;
                try {
                    isLoggedIn = login(scanner, client);
                } catch (RuntimeException | IllegalAccessException runtimeException) {
                    println(RED_BOLD_BRIGHT + runtimeException.getMessage() + RESET);
                    print(YELLOW_BOLD_BRIGHT + "LOADING");
                    dotLoading();
                }
                if (isLoggedIn) {
                    var cash = "";
                    var cashToProcess = 0.0;
                    var status = CANNOT_PERFORM_OPERATION;
                    try {
                        print(PURPLE_BOLD_BRIGHT);
                        println(
                                (transaction == DEPOSIT) ?
                                        "╔╦╗╔═╗╔═╗╔═╗╔═╗╦╔╦╗\n" +
                                        " ║║║╣ ╠═╝║ ║╚═╗║ ║ \n" +
                                        "═╩╝╚═╝╩  ╚═╝╚═╝╩ ╩ \n" :
                                        (transaction == CHECK_BALANCE) ?
                                                "╔═╗╦ ╦╔═╗╔═╗╦╔═  ╔╗ ╔═╗╦  ╔═╗╔╗╔╔═╗╔═╗\n" +
                                                "║  ╠═╣║╣ ║  ╠╩╗  ╠╩╗╠═╣║  ╠═╣║║║║  ║╣ \n" +
                                                "╚═╝╩ ╩╚═╝╚═╝╩ ╩  ╚═╝╩ ╩╩═╝╩ ╩╝╚╝╚═╝╚═╝\n" :
                                            (transaction == WITHDRAW) ?
                                                    "╦ ╦╦╔╦╗╦ ╦╔╦╗╦═╗╔═╗╦ ╦\n" +
                                                    "║║║║ ║ ╠═╣ ║║╠╦╝╠═╣║║║\n" +
                                                    "╚╩╝╩ ╩ ╩ ╩═╩╝╩╚═╩ ╩╚╩╝\n" :
                                                    (transaction == LOAN) ?
                                                            "╦  ╔═╗╔═╗╔╗╔\n" +
                                                            "║  ║ ║╠═╣║║║\n" +
                                                            "╩═╝╚═╝╩ ╩╝╚╝\n" :
                                                            RED_BOLD_BRIGHT + "UNKNOWN PROCESSS"
                        );
                        if (transaction != CHECK_BALANCE) {
                            println(YELLOW_BOLD_BRIGHT + ": ENTER CASH AMOUNT : ");
                            print(RED_BOLD_BRIGHT + ">>>:  " + RESET);
                            cash = scanner.nextLine().trim();
                            if (isWholeNumber().or(isDecimalNumber()).negate().test(cash)) throw new IllegalStateException("\nCASH SHOULD BE A NUMBER\n");
                            cashToProcess = Double.parseDouble(cash);
                        }
                        if ((cashToProcess < 100) && transaction == DEPOSIT) throw new IllegalStateException("\nCASH AMOUNT TO DEPOSIT SHOULD NOT BE LESS THAN 100\n");
                        if ((cashToProcess >= (80.0 / 100.0) * client.savings()) && transaction == WITHDRAW) throw new IllegalStateException("\nCASH TO WITHDRAW SHOULD NOT BE GREATER THAN 80% \nOR EQUAL TO CURRENT SAVINGS\n");
                        if (transaction == DEPOSIT) status = service.updateClientSavingsByAccountNumber().apply(client.accountNumber(), client.savings() + cashToProcess);
                        if (transaction == CHECK_BALANCE) cashToProcess = service.getClientSavingsByAccountNumber().apply(client.accountNumber());
                        else if (transaction == WITHDRAW) status = service.updateClientSavingsByAccountNumber().apply($an, (client.savings() - cashToProcess));
                        else if (transaction == LOAN) status = service.requestLoan().apply(
                                new Loan(
                                        client.accountNumber(),
                                        LocalDate.now(),
                                        cashToProcess,
                                        true
                                ));
                        var message = switch (transaction) {
                            case DEPOSIT -> status == SUCCESS ? BLUE_BOLD_BRIGHT + "CASH DEPOSITED SUCCESSFULLY" + RESET : RED_BOLD_BRIGHT + "ERROR DEPOSITING CASH" + RESET;
                            case CHECK_BALANCE -> String.format(BLUE_BOLD_BRIGHT + "ACCOUNT BALANCE: %s%s%s", CYAN_BOLD_BRIGHT,
                                    Currency.getInstance("PHP").getSymbol().concat(NumberFormat.getInstance().format(cashToProcess))
                                    , RESET);
                            case WITHDRAW -> status == SUCCESS ? BLUE_BOLD_BRIGHT + "CASH WITHDRAWED SUCCESSFULLY" + RESET : RED_BOLD_BRIGHT + "ERROR WITHDRAWING CASH" + RESET;
                            case LOAN -> status == SUCCESS ? BLUE_BOLD_BRIGHT + "LOAN REQUEST HAS BEEN SENT\nA CONFIRMATION MESSAGE WILL BE SENT TO YOU SOON"  + RESET : RED_BOLD_BRIGHT + "ERROR LOANING CASH" + RESET;
                            default -> throw new IllegalStateException(String.format("UNKNOWN PROCESSS: %d", transaction));
                        };
                        println(message);
                        if (transaction != CHECK_BALANCE) {
                            print(YELLOW_BOLD_BRIGHT + "LOADING");
                            dotLoading();
                        }
                    } catch (RuntimeException runtimeException) {
                        println(RED_BOLD_BRIGHT + runtimeException.getMessage() + RESET);
                        print(YELLOW_BOLD_BRIGHT + "LOADING");
                        dotLoading();
                    }
                }
            }

            // TODO: implement and add comment
            private static void viewMessages(Scanner scanner) {
                println(PURPLE_BOLD_BRIGHT + "MESSAGES" + RESET);
                service.getMessage().apply($an)
                        .entrySet()
                        .stream()
                        .filter(e -> e.getKey().equals($an))
                        .map(Map.Entry::getValue)
                        .flatMap(Collection::stream)
                        .toList()
                        .forEach(Print::println);
            }

            /**
             * Used to check the pin of the account before proceeding with a transaction.
             * Locks the account if pin is incorrect 3 times.
             * @param scanner a {@code Scanner} object needed for user input.
             * @param client the {@code Client} object
             * @return {@code true} if logged-in.
             * @throws IllegalAccessException if the account is locked and cannot be accessed.
             * @throws IllegalStateException if the account is locked during pin input.
             */
            private static boolean login(Scanner scanner, Client client) throws IllegalAccessException, IllegalStateException {
                if (client.isLocked()) throw new IllegalAccessException("\nACCOUNT IS LOCKED\nCANNOT PROCEED TRANSACTION\n");
                var isLoggedIn = false;
                var attempts = 3;
                while (attempts != 0) {
                    print(BLUE_BOLD_BRIGHT + "ENTER PIN: ");
                    var pin = scanner.nextLine().trim();
                    if (!client.pin().equals(pin)) {
                        attempts--;
                        println(RED_BOLD_BRIGHT + "\nINVALID PIN\n" + RESET);
                    }
                    else {
                        isLoggedIn = true;
                        break;
                    }
                    if (attempts == 0) {
                        service.updateClientAccountStatusByAccountNumber().apply(client.accountNumber(), true);
                        throw new IllegalStateException(LOCKED_ACCOUNT_MESSAGE + RESET);
                    }
                }
                return isLoggedIn;
            }

        }

        /**
         * class used for managing client messages.
         */
        protected static class Messages {

            private static void loadMessages() {

            }
        }

        /**
         * interface for custom input validator.
         */
        protected static interface Validator extends Predicate<String> {

            static Validator isValidFormat() {
                return input -> Pattern.compile("^\\d{1,2}\\s\\d{9}$").matcher(input).matches();
            }
        }
    }

    /**
     * The Main interface.
     * @param scanner the scanner needed for keyboard input.
     * @return a {@code Role}.
     * @throws IllegalArgumentException if any of the input is not valid.
     * @throws IllegalAccessException if the account does not exist.
     */
    private static Role home(Scanner scanner) throws IllegalArgumentException, IllegalAccessException {
        print(RED_BOLD_BRIGHT +
                "╔═╗" + CYAN_BOLD + "┬ ┬┌┬┐┌─┐┌┬┐┌─┐┌┬┐┌─┐┌┬┐  \n" + RED_BOLD_BRIGHT +
                "╠═╣" + CYAN_BOLD + "│ │ │ │ ││││├─┤ │ ├┤  ││  \n" + RED_BOLD_BRIGHT +
                "╩ ╩" + CYAN_BOLD + "└─┘ ┴ └─┘┴ ┴┴ ┴ ┴ └─┘─┴┘  \n");
        print(GREEN_BOLD_BRIGHT +
                "╔╦╗" + YELLOW_BOLD + "┌─┐┬  ┬  ┌─┐┬─┐  \n" + GREEN_BOLD_BRIGHT +
                " ║ " + YELLOW_BOLD + "├┤ │  │  ├┤ ├┬┘  \n" + GREEN_BOLD_BRIGHT +
                " ╩ " + YELLOW_BOLD + "└─┘┴─┘┴─┘└─┘┴└─  \n");
        println(BLUE_BOLD_BRIGHT +
                "╔╦╗" + PURPLE_BOLD + "┌─┐┌─┐┬ ┬┬┌┐┌┌─┐\n" + BLUE_BOLD_BRIGHT +
                "║║║" + PURPLE_BOLD + "├─┤│  ├─┤││││├┤ \n" + BLUE_BOLD_BRIGHT +
                "╩ ╩" + PURPLE_BOLD + "┴ ┴└─┘┴ ┴┴┘└┘└─┘" + RESET);
        println(YELLOW_BRIGHT + "ENTER YOUR ACCOUNT NUMBER" + RESET);
        print(RED_BOLD_BRIGHT + ">>>: " + RESET);
        var input = scanner.nextLine().trim();
        if (input.equals(SecurityUtil.decrypt(Machine.$adm))) return ADMIN;
        if (input.equals(SecurityUtil.decrypt("dGVybWluYXRl"))) exit(0);
        checkAccountNumberInput(input);
        if (Machine.searchLockedAccount(input)) throw new IllegalAccessException(Machine.LOCKED_ACCOUNT_MESSAGE + RESET);
        var result = Machine.searchAccount(input);
        if (!result) throw new IllegalAccessException("\nACCOUNT DOES NOT EXIST\n");
        else Machine.$an = input;
        return CLIENT;
    }

    /**
     * Cheks the account number that was inputted if valid ornot.
     * @param accountNumber the account number inputted.
     * @throws IllegalArgumentException if input is invalid.
     */
    private static void checkAccountNumberInput(String accountNumber) throws IllegalArgumentException {
        if (isDecimalNumber().test(accountNumber)) throw new IllegalArgumentException("\nACCOUNT NUMBER IS A WHOLE NUMBER\n");
        else if (isWholeNumber().negate().test(accountNumber)) throw new IllegalArgumentException("\nACCOUNT NUMBER IS A NUMBER\n");
        else if (isWholeNumber().negate().test(accountNumber) || (accountNumber.length() < 9 || accountNumber.length() > 9)) throw new IllegalArgumentException("\nACCOUNT NUMBER SHOULD BE 9 DIGITS\n");
    }

    /**
     * Creates a dot loading.
     */
    private static void dotLoading() {
        final var RANDOM = new Random();
        try {
            for (int i = 1; i <= 3; i++) {
                print(".");
                TimeUnit.MILLISECONDS.sleep(RANDOM.nextInt(700) + 1);
            }
            println(RESET + "\n");
        } catch (InterruptedException ignored) {}
    }

    /**
     * Pause the code execution.
     * Continues when a key is pressed.
     */
    private static void pause() {
        print(YELLOW_BOLD + ": PRESS ENTER TO CONTINUE :" + RESET);
        new Scanner(System.in).nextLine();
        println();
    }
}



