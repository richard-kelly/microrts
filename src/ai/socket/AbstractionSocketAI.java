/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ai.socket;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.AIWithComputationBudget;
import ai.core.ParameterSpecification;
import ai.evaluation.EvaluationFunction;
import ai.evaluation.SimpleSqrtEvaluationFunction3;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import util.XMLWriter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class AbstractionSocketAI extends AbstractionLayerAI {
    public static int DEBUG = 0;

    public static final int LANGUAGE_XML = 1;
    public static final int LANGUAGE_JSON = 2;

    int communication_language = LANGUAGE_JSON;
    String serverAddress = "127.0.0.1";
    int serverPort = 9898;
    Socket socket = null;
    BufferedReader in_pipe = null;
    PrintWriter out_pipe = null;

    UnitTypeTable utt = null;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType lightType;
    UnitType heavyType;
    UnitType rangedType;

    EvaluationFunction evaluationFunction;

    public AbstractionSocketAI(UnitTypeTable a_utt) {
        super(new AStarPathFinding(), -1,-1);
        utt = a_utt;
        evaluationFunction = new SimpleSqrtEvaluationFunction3();
        initUnitTypes();
        try {
            connectToServer();
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    public AbstractionSocketAI(int mt, int mi, String a_sa, int a_port, int a_language, UnitTypeTable a_utt) {
        super(new AStarPathFinding(), mt, mi);
        serverAddress = a_sa;
        serverPort = a_port;
        communication_language = a_language;
        utt = a_utt;
        evaluationFunction = new SimpleSqrtEvaluationFunction3();
        initUnitTypes();
        try {
            connectToServer();
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    private AbstractionSocketAI(int mt, int mi, UnitTypeTable a_utt, int a_language, Socket socket) {
        super(new AStarPathFinding(), mt, mi);
        communication_language = a_language;
        utt = a_utt;
        evaluationFunction = new SimpleSqrtEvaluationFunction3();
        initUnitTypes();
        try {
            this.socket = socket;
            in_pipe = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out_pipe = new PrintWriter(socket.getOutputStream(), true);

            // Consume the initial welcoming messages from the server
            while(!in_pipe.ready());
            while(in_pipe.ready()) in_pipe.readLine();

            if (DEBUG>=1) System.out.println("SocketAI: welcome message received");
            reset();
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a SocketAI from an existing socket.
     * @param mt The time budget in milliseconds.
     * @param mi The iterations budget in milliseconds
     * @param a_utt The unit type table.
     * @param a_language The communication layer to use.
     * @param socket The socket the ai will communicate over.
     */
    public static AbstractionSocketAI createFromExistingSocket(int mt, int mi, UnitTypeTable a_utt, int a_language, Socket socket) {
        return new AbstractionSocketAI(mt, mi, a_utt, a_language, socket);
    }
    
    
    public void connectToServer() throws Exception {
        // Make connection and initialize streams
        socket = new Socket(serverAddress, serverPort);
        in_pipe = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out_pipe = new PrintWriter(socket.getOutputStream(), true);

        // Consume the initial welcoming messages from the server
        while(!in_pipe.ready());
        while(in_pipe.ready()) in_pipe.readLine();

        if (DEBUG>=1) System.out.println("SocketAI: welcome message received");
            
        reset();
    }

    private void initUnitTypes() {
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        lightType = utt.getUnitType("Light");
        heavyType = utt.getUnitType("Heavy");
        rangedType = utt.getUnitType("Ranged");
    }

    @Override
    public void reset(UnitTypeTable a_utt) {
        utt = a_utt;
        initUnitTypes();
        reset();
    }

    @Override
    public void reset() {
        super.reset();
        try {
            // set the game parameters:
            out_pipe.append("budget " + TIME_BUDGET + " " + ITERATIONS_BUDGET + "\n");
            out_pipe.flush();

            if (DEBUG>=1) System.out.println("SocketAI: budgetd sent, waiting for ack");
            
            // wait for ack:
            in_pipe.readLine();
            while(in_pipe.ready()) in_pipe.readLine();

            if (DEBUG>=1) System.out.println("SocketAI: ack received");

            // send the utt:
            out_pipe.append("utt\n");
            if (communication_language == LANGUAGE_XML) {
                XMLWriter w = new XMLWriter(out_pipe, " ");
                utt.toxml(w);
                w.flush();
                out_pipe.append("\n");
                out_pipe.flush();                
            } else if (communication_language == LANGUAGE_JSON) {
                utt.toJSON(out_pipe);
                out_pipe.append("\n");
                out_pipe.flush();
            } else {
                throw new Exception("Communication language " + communication_language + " not supported!");
            }
            if (DEBUG>=1) System.out.println("SocketAI: UTT sent, waiting for ack");
            
            // wait for ack:
            in_pipe.readLine();
            
            // read any extra left-over lines
            while(in_pipe.ready()) in_pipe.readLine();
            if (DEBUG>=1) System.out.println("SocketAI: ack received");

        }catch(Exception e) {
            e.printStackTrace();
        }
    }
    

    @Override
    public PlayerAction getAction(int player, GameState gs) throws Exception {
        // send the game state:
        out_pipe.append("getAction " + player + "\n");
        out_pipe.append(evaluationFunction.evaluate(player, (player + 1) % 2, gs) + "\n");
        if (communication_language == LANGUAGE_XML) {
            XMLWriter w = new XMLWriter(out_pipe, " ");
            gs.toxml(w);
            w.getWriter().append("\n");
            w.flush();

            // wait to get an action:
//            while(!in_pipe.ready()) {
//                Thread.sleep(0);
//                if (DEBUG>=1) System.out.println("waiting");
//            }

            // parse the action:
            String actionString = in_pipe.readLine();
            if (DEBUG>=1) System.out.println("action received from server: " + actionString);
            Element action_e = new SAXBuilder().build(new StringReader(actionString)).getRootElement();
            PlayerAction pa = PlayerAction.fromXML(action_e, gs, utt);
            pa.fillWithNones(gs, player, 10);
            return pa;
        } else if (communication_language == LANGUAGE_JSON) {
            gs.toJSON(out_pipe);
            out_pipe.append("\n");
            out_pipe.flush();
            
            // wait to get an action:
            //while(!in_pipe.ready());
                
            // parse the action:
            String actionString = in_pipe.readLine();
            JsonArray a = Json.parse(actionString).asArray();
            for(JsonValue v : a.values()) {
                JsonObject o = v.asObject();
                parseAbstractActionJSON(o, player, gs);
            }

            // This method simply takes all the unit actions executed so far, and packages them into a PlayerAction
            return translateActions(player, gs);
        } else {
            throw new Exception("Communication language " + communication_language + " not supported!");
        }        
    }

    private void parseAbstractActionJSON(JsonObject o, int player, GameState gs) {
        int id = o.getInt("unitID", -1);
        Unit u = gs.getUnit(id);
        String actionType = o.getString("type", "");
        if (actionType.equals("move")) {
            int x = o.getInt("x", -1);
            int y = o.getInt("y", -1);
            move(u, x, y);
        }
        else if (actionType.equals("train")) {
            String toTrain = o.getString("unitType", "");
            if (toTrain.equals("Worker")) {
                train(u, workerType);
            }
            else if (toTrain.equals("Light")) {
                train(u, lightType);
            }
            else if (toTrain.equals("Heavy")) {
                train(u, heavyType);
            }
            else if (toTrain.equals("Ranged")) {
                train(u, rangedType);
            }
        }
        else if (actionType.equals("build")) {
            String toBuild = o.getString("unitType", "");
            int x = o.getInt("x", -1);
            int y = o.getInt("y", -1);
            if (toBuild.equals("Base")) {
                build(u, baseType, x, y);
            }
            else if (toBuild.equals("Barracks")) {
                build(u, barracksType, x, y);
            }
        }
        else if (actionType.equals("harvest")) {
            int x = o.getInt("x", -1);
            int y = o.getInt("y", -1);
            Unit target = gs.getPhysicalGameState().getUnitAt(x, y);
            Unit base = getClosestBase(target, player, gs);
            harvest(u, target, base);
        }
        else if (actionType.equals("attack")) {
            int x = o.getInt("x", -1);
            int y = o.getInt("y", -1);
            Unit target = gs.getPhysicalGameState().getUnitAt(x, y);
            attack(u, target);
        }
    }

    private Unit getClosestBase(Unit u, int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        Unit closestBase = null;

        int closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestBase == null || d < closestDistance) {
                    closestBase = u2;
                    closestDistance = d;
                }
            }
        }
        return closestBase;
    }
    

    @Override
    public void preGameAnalysis(GameState gs, long milliseconds) throws Exception 
    {
        // send the game state:
        out_pipe.append("preGameAnalysis " + milliseconds + "\n");
        switch (communication_language) {
            case LANGUAGE_XML:
                XMLWriter w = new XMLWriter(out_pipe, " ");
                gs.toxml(w);
                w.flush();
                out_pipe.append("\n");
                out_pipe.flush();
                // wait for ack:
                in_pipe.readLine();
                break;
                
            case LANGUAGE_JSON:
                gs.toJSON(out_pipe);
                out_pipe.append("\n");
                out_pipe.flush();
                // wait for ack:
                in_pipe.readLine();
                break;
                
            default:
                throw new Exception("Communication language " + communication_language + " not supported!");        
        }
    }

    
    @Override
    public void preGameAnalysis(GameState gs, long milliseconds, String readWriteFolder) throws Exception 
    {
        // send the game state:
        out_pipe.append("preGameAnalysis " + milliseconds + "  \""+readWriteFolder+"\"\n");
        switch (communication_language) {
            case LANGUAGE_XML:
                XMLWriter w = new XMLWriter(out_pipe, " ");
                gs.toxml(w);
                w.flush();
                out_pipe.append("\n");
                out_pipe.flush();
                // wait for ack:
                in_pipe.readLine();
                break;
                
            case LANGUAGE_JSON:
                gs.toJSON(out_pipe);
                out_pipe.append("\n");
                out_pipe.flush();
                // wait for ack:
                in_pipe.readLine();
                break;
                
            default:
                throw new Exception("Communication language " + communication_language + " not supported!");        
        }
    }
    
    
    @Override
    public void gameOver(int winner) throws Exception
    {
        // send the game state:
        out_pipe.append("gameOver " + winner + "\n");
        out_pipe.flush();
                
        // wait for ack:
        in_pipe.readLine();        
    }
    
    
    @Override
    public AI clone() {
        return new AbstractionSocketAI(TIME_BUDGET, ITERATIONS_BUDGET, serverAddress, serverPort, communication_language, utt);
    }

    public void setServerAddress(String server) {
        serverAddress = server;
    }

    public void setServerPort(int port) {
        serverPort = port;
    }

    public void setLanguage(int language) {
        communication_language = language;
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> l = new ArrayList<>();
        
        l.add(new ParameterSpecification("ServerAddress", String.class, "127.0.0.1"));
        l.add(new ParameterSpecification("ServerPort", int.class, 9898));
        l.add(new ParameterSpecification("Language", int.class, LANGUAGE_JSON));
        
        return l;
    }
}
