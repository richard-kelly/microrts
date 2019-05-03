package tournaments;

import ai.core.AI;
import gui.frontend.FEStatePane;
import rts.units.UnitTypeTable;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class RemoteFixedOpponentsTournamentRunner {

    private static String[] AIClassNames = {
            "PassiveAI",
            "MouseController",
            "RandomAI",
            "RandomBiasedAI",
            "WorkerRush",
            "LightRush",
            "HeavyRush",
            "RangedRush",
            "WorkerDefense",
            "LightDefense",
            "HeavyDefense",
            "RangedDefense",
            "POWorkerRush",
            "POLightRush",
            "POHeavyRush",
            "PORangedRush",
            "WorkerRushPlusPlus",
            "CRush_V1",
            "CRush_V2",
            "PortfolioAI",
            "PGSAI",
            "IDRTMinimax",
            "IDRTMinimaxRandomized",
            "IDABCD",
            "MonteCarlo",
            "LSI",
            "UCT",
            "UCTUnitActions",
            "UCTFirstPlayUrgency",
            "DownsamplingUCT",
            "NaiveMCTS",
            "BS3_NaiveMCTS",
            "MLPSMCTS",
            "AHTNAI",
            "InformedNaiveMCTS",
            "PuppetSearchMCTS",
            "SCV",
            "SocketAI"
    };

    private static int getIndex(String[] array, String val) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(val)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @param args
     * utt name
     * name of selected AI
     * iterations (rounds)
     * max game length (game frames)
     * time budget (ms)
     * pre-analysis budget (ms)
     * full observability (true/false)
     * store traces (true/false)
     * number_of_opponents
     * ... opponent names
     * number_of_maps
     * ... map names
     */
    public static void main(String[] args) {
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        int uttIndex = getIndex(FEStatePane.unitTypeTableNames, args[0]);
        int selectedAIIndex = getIndex(AIClassNames, args[1]);
        int iterations = Integer.parseInt(args[2]);
        int maxGameLength = Integer.parseInt(args[3]);
        int timeBudget = Integer.parseInt(args[4]);
        int iterationsBudget = -1;
        int preAnalysisBudget = Integer.parseInt(args[5]);

        boolean fullObservability = args[6].equals("true");
        boolean storeTraces = args[7].equals("true");
        boolean timeOutCheck = false;
        boolean gcCheck = false;
        boolean preGameAnalysis = preAnalysisBudget > 0;

        int numOpponentAIs = Integer.parseInt(args[8]);
        String opponentAINames[] = new String[numOpponentAIs];
        for (int i = 0; i < numOpponentAIs; i++) {
            opponentAINames[i] = args[i + 9];
        }

        int numMaps = Integer.parseInt(args[9 + numOpponentAIs]);
        List<String> maps = new ArrayList<>();
        for (int i = 0; i < numMaps; i++) {
            maps.add(args[i + 10 + numOpponentAIs]);
        }

        try {
            // get all the necessary info:
            UnitTypeTable utt = FEStatePane.unitTypeTables[uttIndex];
            List<AI> selectedAIs = new ArrayList<>();
            Constructor cons = FEStatePane.AIs[selectedAIIndex].getConstructor(UnitTypeTable.class);
            selectedAIs.add((AI)cons.newInstance(utt));

            List<AI> opponentAIs = new ArrayList<>();
            for(int i = 0; i < numOpponentAIs; i++) {
                int oppAIIndex = getIndex(AIClassNames, opponentAINames[i]);
                Constructor oppCons = FEStatePane.AIs[oppAIIndex].getConstructor(UnitTypeTable.class);
                opponentAIs.add((AI)oppCons.newInstance(utt));
            }

            String prefix = "microrts/tournament_";
            int idx = 0;
//                    String sufix = ".tsv";
            File file;
            do {
                idx++;
                file = new File(prefix + idx);
            }while(file.exists());
            file.mkdirs();
            String tournamentfolder = prefix + idx;
            final File fileToUse = new File(tournamentfolder + "/tournament.csv");
            final String tracesFolder = (storeTraces ? tournamentfolder + "/traces":null);

            try {
                Writer writer = new FileWriter(fileToUse);
                FixedOpponentsTournament.runTournament(selectedAIs, opponentAIs, maps,
                        iterations, maxGameLength, timeBudget, iterationsBudget,
                        preAnalysisBudget, 1000, // 1000 is just to give 1 second to the AIs to load their read/write folder saved content
                        fullObservability, timeOutCheck, gcCheck, preGameAnalysis,
                        utt, tracesFolder,
                        writer, null,
                        tournamentfolder);
                writer.close();
            } catch(Exception e2) {
                e2.printStackTrace();
            }

        }catch(Exception ex) {
            ex.printStackTrace();
        }
    }
}
