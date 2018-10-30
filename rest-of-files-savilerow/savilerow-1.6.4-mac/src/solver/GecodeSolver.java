package savilerow.solver;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/

import java.util.*;
import java.io.*;

//import com.google.gson.Gson;

import savilerow.model.*;
import savilerow.expression.*;
import savilerow.CmdFlags;
import savilerow.eprimeparser.EPrimeReader;

public class GecodeSolver extends Solver
{
    // gecodename is the name of the gecode-flatzinc binary -- usually fzn-gecode.
    // filename is the name of the minion input file. 
    // m is the model 
    public void findSolutions(String gecodename, String filename, Model m) throws IOException,  InterruptedException
    {
        double srtime=(((double) System.currentTimeMillis() - CmdFlags.startTime) / 1000);
        
        runGecode(gecodename, filename, m);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //   Private methods. 
    
    private void runGecode(String gecodename, String filename, Model m) throws IOException,  InterruptedException
    {
        CmdFlags.runningSolver=true;  // Prevents SR's timeout from kicking in. 
        
        try
        {
            ArrayList<String> command = new ArrayList<String>();
            command.add(gecodename);
            
            if(CmdFlags.getFindAllSolutions()) {
                if(m.objective!=null) {
                    CmdFlags.println("WARNING: Ignoring -all-solutions flag because it cannot be used with optimisation.");
                    CmdFlags.setFindAllSolutions(false);
                }
                else {
                    command.add("-n");
                    command.add("0");   // Set number of solutions to 0 -- means find all. 
                }
            }
            
            if(CmdFlags.getFindNumSolutions()>-1) {
                if(m.objective!=null) {
                    CmdFlags.println("WARNING: Ignoring -num-solutions flag because it cannot be used with optimisation.");
                    CmdFlags.setFindNumSolutions(-1);
                }
                else {
                    command.add("-n");
                    command.add(""+CmdFlags.getFindNumSolutions());
                }
            }
            
            command.addAll(CmdFlags.getSolverExtraFlags());
            
            command.add(filename);
            
            ArrayList<String> stderr_lines=new ArrayList<String>();
            
            ReadGecodeOutput rgo=new ReadGecodeOutput(this, m.global_symbols);
            
            int exitValue=RunCommand.runCommand(command, stderr_lines, rgo);
            
            if(stderr_lines.size()!=0 || exitValue!=0) {
                CmdFlags.println("Gecode exited with error code:"+exitValue+" and message:");
                CmdFlags.println(stderr_lines);
                CmdFlags.rmTempFiles();
            }
        }
        catch(IOException e1) {
            System.out.println("IOException.");
            CmdFlags.rmTempFiles();
            throw e1;
        }
        catch(InterruptedException e2) {
            System.out.println("InterruptedException.");
            CmdFlags.rmTempFiles();
            throw e2;
        }
        
    }
    
    // To be used when parsing all/multiple solutions.
    Solution parseOneSolverSolution(SymbolTable st, BufferedReader in) {
        
        // Grab the text from the current point to the line of 10 minus signs
        try {
            ArrayList<String> solversol=new ArrayList<String>();
            
            String s=in.readLine();
            if(s==null) {
                // Reached the end of the stream without seeing ---------- 
                return null;
            }
            while(!s.equals("----------")) {
                solversol.add(s);
                s=in.readLine();
                if(s==null) {
                    return null;
                }
            }
            
            Solution sol=solverSolToAST(solversol, st);
            return sol;
        }
        catch(IOException e) {
            return null;
        }
    }
    
    Solution parseLastSolverSolution(SymbolTable st, BufferedReader in) {
        ArrayList<String> solversol=null;
        
        while(true) {
            ArrayList<String> solversol1=new ArrayList<String>();
            // Grab the text from the current point to the line of 10 minus signs
            try {
                String s=in.readLine();
                if(s==null) {
                    // Reached the end of the stream without seeing ---------- 
                    // get out of the while loop. 
                    break;
                }
                
                if(s.equals("=====UNSATISFIABLE=====") || s.equals("==========")) {
                    // No (further) solution -- just break
                    break;
                }
                
                while(! (s.equals("----------") )) {
                    solversol1.add(s);
                    s=in.readLine();
                    if(s==null) {
                        break;
                    }
                }
                solversol=solversol1;
            }
            catch(IOException e) {
                break;
            }
        }
        
        if(solversol!=null) {
            Solution sol=solverSolToAST(solversol, st);
            return sol;
        }
        else {
            return null;
        }
    }
    
    // Takes a solution printed out by Gecode
    // and turns it into a hashmap mapping variable name to value.
    HashMap<String, Long> readAllAssignments(ArrayList<String> gecodesol, SymbolTable st) {
        HashMap<String, Long> collect_all_values=new HashMap<String, Long>();
        
        // Each string contains an assignment
        //  var = <num>;
        
        for(int i=0; i<gecodesol.size(); i++) {
            String assign=gecodesol.get(i);
            String[] sp=assign.split(" = ");
            assert sp.length==2;
            String[] num=sp[1].split(";");  // chop off the ; and newline
            
            collect_all_values.put(sp[0], Long.parseLong(num[0].trim()));
        }
        
        return collect_all_values;
    }
    
    
    // Thread to read the standard out of gecode and directly parse it, create solution files etc.
    class ReadGecodeOutput extends ReadProcessOutput {
        ReadGecodeOutput(GecodeSolver _gs, SymbolTable _st) {
            gs=_gs;
            st=_st;
        }
        
        BufferedReader br;
        GecodeSolver gs;
        SymbolTable st;
        
        public void giveInputStream(BufferedReader _br) {
            br=_br;
        }
        
        public void run() {
            if((!CmdFlags.getFindAllSolutions()) && CmdFlags.getFindNumSolutions()==-1) {
                // Find one solution only. Takes the last solution because for optimisation that will be the optimal one. 
                Solution sol = gs.parseLastSolverSolution(st, br);
                
                if(sol!=null || st.m.incumbentSolution!=null) {
                    gs.createSolutionFile( ((sol!=null)?sol:st.m.incumbentSolution), CmdFlags.solutionfile);
                }
            }
            else {
                // Multiple solutions. 
                gs.parseAllSolverSolutions(st, br);
            }
        }
    }
    
}
