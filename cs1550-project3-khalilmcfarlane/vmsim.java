import java.io.*;
import java.util.*;
import java.lang.*;

public class vmsim {
    static char mode = ' '; // Access Type: indic. whether the access is a load or a store
    static int address = 0; // Second input per line of trace file
    static int process = 0; // indicates whether first or second process is running
    static int dirtyBit = 0;
    static int memAccesses = 0;
    static int offset = 0;
    static int processSize0 = 0;
    static int processSize1 = 0;
    static int pageFaults = 0;
    static int writeCount = 0;
    public static void main(String[] args) throws IOException, FileNotFoundException {
        String algorithmType = "";
        String traceFile = "";
        int numFrames = 0;
        int pageSize = 0;
        int firstMemSplit = 0;
        int secondMemSplit = 0;
        
        // assign variables based on args
        for(int i = 0; i < args.length;i++) {
            if(args[i].equalsIgnoreCase("-a")) {
                algorithmType = args[i+1];
            }
            if(args[i].equalsIgnoreCase("-n")) {
                numFrames = Integer.parseInt(args[i+1]); 
            }
            if(args[i].equalsIgnoreCase("-p")) {
                pageSize = Integer.parseInt(args[i+1]);
                //pageSize = pageSize * 1024;
            }
            if(args[i].equalsIgnoreCase("-s")) {
                String[] ratioSplit = args[i+1].split(":");
                firstMemSplit = Integer.valueOf(ratioSplit[0]);
                secondMemSplit = Integer.valueOf(ratioSplit[1]);
            }
            else if(i == args.length-1) {
                traceFile = args[i];
            }

        }

        offset = (int)(Math.log(pageSize*1024)/ Math.log(2));
        processSize0 = numFrames / (firstMemSplit + secondMemSplit) * firstMemSplit;
        processSize1 = numFrames - processSize0; 
        
        // Need a for loop that loops for as many frames
        if(algorithmType.equalsIgnoreCase("lru")) {
            LRU(numFrames, traceFile);
        }
        else if(algorithmType.equalsIgnoreCase("opt")) {
            OPT(numFrames, traceFile);
        }

        System.out.println("Algorithm: " + algorithmType.toUpperCase());
        System.out.println("Number of frames: " + numFrames);
        System.out.println("Page size: " + pageSize + " KB");
        System.out.println("Total memory accesses: " + memAccesses);
        System.out.println("Total page faults: " + pageFaults);
        System.out.println("Total writes to disk: " + writeCount);


    }

    public static void LRU(int frames, String tracefile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(tracefile));
        int frameNum0 = 0;
        int frameNum1 = 0;
        int hit = 0;
        int hit1 = 0;
        HashMap<Long, Boolean> dbitMap = new HashMap<Long, Boolean>();
        HashMap<Long, Boolean> dbitMap1 = new HashMap<Long, Boolean>();
        LinkedList<Long> procList0 = new LinkedList<Long>();
        LinkedList<Long> procList1 = new LinkedList<Long>();
          
        try {
            while(br.ready()) {
                String[] inputLine = br.readLine().split(" ");
                Long pageAddress = Long.decode(inputLine[1].substring(0));
                // shift bits to retain only page number
                pageAddress = pageAddress >>> offset;
                
                // I will use a singular linked list for lru
                // Keep linked list size the same as frame size
                // remove head when there's a page fault
                // if theres a hit REMEMBER TO REMOVE HIT VALUE
                // AND BRING IT TO TAIL BC IT's RECENTLY USED
                int procNum = Integer.parseInt(inputLine[2]);
                if(procNum == 0) {
                    if(inputLine[0].equalsIgnoreCase("s")) {
                    
                        dbitMap.put(pageAddress, true);
                    }

                    // We are now in process 0's share of the memory
                    if(!(procList0.contains(pageAddress))) {
                        // if its not in page table
                        // thats automatically a page fault
                        pageFaults++;
                        
                        // Initial filling up of page table
                        if(frameNum0 < processSize0) {
                                procList0.add(pageAddress);
                                frameNum0++;
                        }
                           // If we are at max capacity,
                           // Remove the head of list
                           // Insert new address into list
                        else if(procList0.size() == processSize0) {
                               Long j = procList0.removeFirst();
                                if(dbitMap.containsKey(j)) {
                                    writeCount++;
                                    dbitMap.remove(j);
                                }
                                procList0.add(pageAddress);
                        }
                    }
                  
                    // if page table already contains address
                    // You actually want to remove where the address is
                    // And insert into the end of linked list bc it's most
                    // recently used
                    else if(procList0.contains(pageAddress)) {
                        hit++;
                        procList0.remove(pageAddress);
                        procList0.add(pageAddress);
                    }
                }
                
                if(procNum == 1) {
                    /*
                        this is my weird way of handling a change of dirty bit
                        I Filled a map full of trues and see if
                        that page number is a key in map
                        I delete occurance to not worry about repeats 
                    */
                    if(inputLine[0].equalsIgnoreCase("s")) {
                        dbitMap1.put(pageAddress, true);
                    }

                    if(!(procList1.contains(pageAddress))) {
                        pageFaults++;

                        if(frameNum1 < processSize1) {
                            procList1.add(pageAddress);
                            frameNum1++;
                        }
                            // we evict the least recently used
                            // inc page faults + write to discs
                        if(procList1.size() == processSize1) {
                            Long j = procList1.removeFirst();
                            if(dbitMap1.containsKey(j)) {
                                writeCount++;
                                dbitMap1.remove(j);
                            }
                            procList1.add(pageAddress);
                        }                           
                    }
                  
                    // if page table already contains address
                    else if(procList1.contains(pageAddress)) {
                        hit1++;
                        procList1.remove(pageAddress);
                        procList1.add(pageAddress);
                    }
                }
                memAccesses++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
            br.close();
      }

    public static void OPT(int frames, String tracefile) throws IOException {
        Hashtable<Long, LinkedList<Integer>> entriesProc0 = new Hashtable<Long, LinkedList<Integer>>();
        Hashtable<Long, LinkedList<Integer>> entriesProc1 = new Hashtable<Long, LinkedList<Integer>>();

        /*
            Create lists that are size of each amt of frames the proc gets
            ex: if proc0 had 3 frames allocated, that's the size
            will use lists in algorithm to remove + add new values
            This will work with hashtable
            As I loop thru table, if list does not contain val, 
            pagefault++, evict one that is latest used in future of list
        */
        
        // In my implementation, I use an arraylist as my page table
        ArrayList<Long> ProcList0 = new ArrayList<Long>();
        ArrayList<Long> ProcList1 = new ArrayList<Long>();

        // Data structures to keep track of dirty bit
        HashMap<Long, Boolean> dbitMap = new HashMap<Long, Boolean>();
        HashMap<Long, Boolean> dbitMap1 = new HashMap<Long, Boolean>();


        BufferedReader br = new BufferedReader(new FileReader(tracefile));
        int currentLine = 0;
        try {
            while(br.ready()) {
                String[] inputLine = br.readLine().split(" ");
                int procNum = Integer.parseInt(inputLine[2]);
                Long pageAddress = Long.decode(inputLine[1].substring(0));
                // shift bits to retain only page number
                pageAddress = pageAddress >>> offset;
                memAccesses++;
                
                if(procNum == 0) {
                    if(entriesProc0.get(pageAddress) == null) {
                        entriesProc0.put(pageAddress, new LinkedList<Integer>());
                        entriesProc0.get(pageAddress).add(currentLine);

                    } else {
                            entriesProc0.get(pageAddress).add(currentLine);
                        }
                }

                if(procNum == 1) {
                    if(entriesProc1.get(pageAddress) == null) {
                         entriesProc1.put(pageAddress, new LinkedList<Integer>());
                         entriesProc1.get(pageAddress).add(currentLine);

                    } else {
                            entriesProc1.get(pageAddress).add(currentLine);
                        }
                }
                currentLine++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        br.close();
        BufferedReader reader = new BufferedReader(new FileReader(tracefile));
        Long j;
        Long k;
        int hit_0 = 0;
        int hit_1 = 0;
        try {
            while(reader.ready()) {
                String[] inputLine = reader.readLine().split(" ");
                Long pageAddress = Long.decode(inputLine[1].substring(0));
                int procNum = Integer.parseInt(inputLine[2]);
                pageAddress = pageAddress >>> offset;
                if(procNum == 0) {                    
                if(inputLine[0].equalsIgnoreCase("s")) {
                    dbitMap.put(pageAddress, true);
                }

                // If the hash table does not contain our key
                if(!(ProcList0.contains(pageAddress))) {
                    pageFaults++;
                  entriesProc0.get(pageAddress).removeFirst();
                    
                    // Initial populating of page table
                    if(ProcList0.size() < processSize0) {
                        ProcList0.add(pageAddress);
                    }
                        // we evict the latest used in future
                        // inc page faults + write to discs
                        else if(ProcList0.size() == processSize0) {
                            j = LongestDistance(ProcList0, entriesProc0);
                            if(dbitMap.containsKey(j)) {
                                writeCount++;
                                dbitMap.remove(j);
                            }
                            ProcList0.remove(j);
                            ProcList0.add(pageAddress);
                           
                        }                                   
                }
              
                // if page table already contains address
                else if(ProcList0.contains(pageAddress)) {
                    hit_0++;
                   entriesProc0.get(pageAddress).removeFirst();
                }
            }
           if(procNum == 1) {               
                if(inputLine[0].equalsIgnoreCase("s")) {
                    dbitMap1.put(pageAddress, true);
                }

                  // If the hash table does not contain our key
                  if(!(ProcList1.contains(pageAddress))) {
                    pageFaults++;
                    entriesProc1.get(pageAddress).removeFirst();
                    
                        // Initial case of filling up page table                    
                        if(ProcList1.size() < processSize1) {
                            ProcList1.add(pageAddress);
                        }
                        
                        // When you need to evict furthest used
                        if(ProcList1.size() == processSize1) {
                            k = LongestDistance(ProcList1, entriesProc1);
                            if(dbitMap1.containsKey(k)) {
                                writeCount++;
                                dbitMap1.remove(k);
                            }
                            ProcList1.remove(k);
                            ProcList1.add(pageAddress);
                        }
                }
                // if page table already contains address
                else if(ProcList1.contains(pageAddress)) {
                    hit_1++;
                    entriesProc1.get(pageAddress).removeFirst();
                }
            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
        reader.close();
       
       
    }

    // Helper method for OPT that finds longest distanced member of list
    // returns address/page number that needs to be removed
    private static long LongestDistance(ArrayList<Long> ProcessList, Hashtable<Long, LinkedList<Integer>> entries) throws Exception {
        long i = 0;
        int furthestKey = 0;
        for(int index = 0; index < ProcessList.size(); index++) {
           
            /*
                If there are no occurences later on the table of that page number
                immediately return that page number
                this will break the tie by preemptively gettind rid of latest
                without considering earliest.
                This works bc I use a list as my real page table, so 
                the earlier index will always be the least recently used
                page number.
            */
            if(entries.get(ProcessList.get(index)).isEmpty()) {
                return ProcessList.get(index);
            } else {
                /*
                     if head of linked list(line num) is equal to furthest distance, 
                     furthest key is one with lower line number
                     .getFirst() is earliest occurence of page number
                     aka lowest line number for respective page number
                */
                if(entries.get(ProcessList.get(index)).getFirst() > furthestKey) {
                    // this line is now furthest key
                    furthestKey = entries.get(ProcessList.get(index)).getFirst();
                  
                    i = ProcessList.get(index);
                }
            }
        }
        return i;
    }
}