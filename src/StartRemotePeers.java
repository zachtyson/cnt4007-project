import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class StartRemotePeers {
    public Vector<RemotePeerInfo> peerInfoVector;
    public void getConfiguration() {
        String st;
        peerInfoVector = new Vector<RemotePeerInfo>();
        try {
            BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
            while((st = in.readLine()) != null) {
                String[] tokens = st.split("\\s+");
                peerInfoVector.addElement(new RemotePeerInfo(tokens[0], tokens[1], tokens[2], tokens[3]));

            }

            in.close();
        }
        catch (Exception ex) {
            System.out.println(ex.toString());
        }
    }
    public void setUpDevelopment() throws Exception{
        // This is a method I made to help with testing on my local machine
        // Basically just sets the local_testing directory to delete all files except 'thefile'
        // And then copies peerProcess.java to each of the peer directories as well as Common.cfg and PeerInfo.cfg
        // This way I can run the program on my local machine and test it without having to deal with all the ssh stuff
        // and manual copying of files
        // I'm not sure if this is the best way to do this, but it works for now
        String localTestingDirectory = "local_testing";
        String[] peerFolders = new String[]{"1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009"};
        //Check if the local_testing directory exists
        File localTestingDir = new File(localTestingDirectory);
        if(!localTestingDir.exists() || !localTestingDir.isDirectory()) {
            // If it doesn't exist, create it
            localTestingDir.mkdir();
        }
        for (String peerFolder : peerFolders) {
            File peerFolderFile = new File(localTestingDir, peerFolder);
            if (!peerFolderFile.exists() || !peerFolderFile.isDirectory()) {
                // Check if folder exists, if not create it
                // I don't think the second check is necessary, since I can't see a scenario where a file would be
                // named '1006' or something with no extension, but I'm not sure
                peerFolderFile.mkdir();
            }
        }
        // Iterate over peerInfoVector and check if the peer has the file
        // If it does, copy the file to the peer's directory
        // If it doesn't, delete all files except 'thefile' from the peer's directory
        String path = System.getProperty("user.dir");
        for (RemotePeerInfo peerInfo : peerInfoVector) {
            //Delete all files except 'thefile'
            File peerFolderFile = new File(localTestingDir, peerInfo.peerId);
            File[] files = peerFolderFile.listFiles();
            if(files != null) {
                for (File file : files) {
                    if (!file.getName().equals("thefile") || !peerInfo.hasFileOnStart) {
                        file.delete();
                    }
                }
            }

            String[] filesToCopy = {"Common.cfg", "PeerInfo.cfg"};
            for (String fileName : filesToCopy) {
                File sourceFile = new File(path, fileName);
                File destinationFile = new File(peerFolderFile, fileName);
                try {
                    Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if(peerInfo.hasFileOnStart) {
                        // If the peer has the file, copy it to the peer's directory
                        File sourceFile2 = new File(path, "thefile");
                        File destinationFile2 = new File(peerFolderFile, "thefile");
                        try {
                            Files.copy(sourceFile2.toPath(), destinationFile2.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            File sourceFile = new File(path+"\\src", "peerProcess.java");
            File destinationFile = new File(peerFolderFile, "peerProcess.java");
            try {
                Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if(peerInfo.hasFileOnStart) {
                    // If the peer has the file, copy it to the peer's directory
                    File sourceFile2 = new File(path, "thefile");
                    File destinationFile2 = new File(peerFolderFile, "thefile");
                    try {
                        Files.copy(sourceFile2.toPath(), destinationFile2.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



    }
    public List<Process> processes = new ArrayList<>();
    public static void main(String[] args) {
        // From my understanding the first argument is the peerID, which we can see
        // in PeerInfo.cfg as well as page 7 of the project description pdf
        // e.g 'java peerProcess 1001'
        // from there it will see the field value of the PeerInfo.cfg and if it is 1 then it will set the
        // bitfield to all 1s, otherwise all 0s.
        // Note that the peers are intended to be started in the order they appear in PeerInfo.cfg
        // That is, that 1001 starts first followed by 1002, 1003, ... etc.
        // The peer that just started should try to make TCP connections to all peers that started before it
        // If it is the first peer, it will simply listen on port 6008.
        // When all processes have started, all peers are connected with all other peers.
        // When a peer is connected to at least one other peer,
        // it starts to exchange pieces as described in the protocol description section.
        // A peer terminates when it finds out that all the peers, not just itself,
        // have downloaded the complete file.
        // Common.cfg contains some config data that all peers should read upon starting
        // UnchokingInterval [int(?)]
        // OptimisticUnchokingInterval [int(?)]
        // FileName [string] - Specifies the name of the file peers are interested in
        // FileSize [int] - Specifies the size of the file in bytes
        // PieceSize [int] - Specifies the size of a piece in bytes
        // NumberOfPreferredNeighbors [int] - Sounds self-explanatory, but I have no idea what this means -Zach
        // TODO: Make some notes and comments on other implementations
        // Below is some code they gave us that they said should help get started
        try {
            StartRemotePeers myStart = new StartRemotePeers();
            myStart.getConfiguration();
            myStart.setUpDevelopment();
            // get current path
            String path = System.getProperty("user.dir");
            // start clients at remote hosts
            for (int i = 0; i < myStart.peerInfoVector.size(); i++) {
                RemotePeerInfo pInfo = myStart.peerInfoVector.elementAt(i);
                System.out.println("Start remote peer " + pInfo.peerId +  " at " + pInfo.peerAddress );
                // Runtime.getRuntime().exec("ssh " + pInfo.peerAddress + " cd " + path + "; java peerProcess " + pInfo.peerId);
                // Commenting this out for testing on my local machine

                //Testing code below
                Process process = getProcess(path, pInfo);
                getProcessOutput(process, myStart);

            }
            System.out.println("Starting all remote peers has done." );

        }
        catch (Exception ex) {
            System.out.println(ex);
        }
    }

    private static Process getProcess(String path, RemotePeerInfo pInfo) throws IOException {
        File dir = new File(path +"/local_testing/"+ pInfo.peerId);
        ProcessBuilder processBuilder = new ProcessBuilder("java", ".\\peerProcess.java", pInfo.peerId);
        processBuilder.directory(dir);
        Process process = processBuilder.start();
        return process;
    }

    private static void getProcessOutput(Process process,StartRemotePeers myStart) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Stdout: " + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println("Stderr: " + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        myStart.processes.add(process);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            myStart.processes.forEach(Process::destroy);
        }));
    }

    public static class RemotePeerInfo {
        public String peerId;
        public String peerAddress;
        public String peerPort;

        public boolean hasFileOnStart;

        public RemotePeerInfo(String peerId, String peerAddress, String peerPort, String hasFileOnStart) {
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.peerPort = peerPort;
            this.hasFileOnStart = !hasFileOnStart.equals("0");
        }
    }

}
