
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

import savilerow.*;
import savilerow.model.*;
import savilerow.expression.*;
import savilerow.CmdFlags;
import savilerow.eprimeparser.EPrimeReader;

//  Subclasses of SATSolver provide the runSatSolver method that returns a string
//  containing the solution literals and a stats object. 

public class LingelingSATSolver extends SATSolver
{
    public LingelingSATSolver(Model _m) {
        super(_m);
    }
    public Pair<ArrayList<String>, Stats> runSatSolver(String satSolverName, String filename, Model m) throws IOException,  InterruptedException
    {
        CmdFlags.runningSolver=true;  // Prevents SR's timeout from kicking in. 
        
        try {
            ArrayList<String> command = new ArrayList<String>();
            command.add(satSolverName);
            command.addAll(CmdFlags.getSolverExtraFlags());
            command.add(filename);
            
            ArrayList<String> stderr_lines=new ArrayList<String>();
            ArrayList<String> stdout_lines=new ArrayList<String>();
            
            ReadProcessOutput rpo=new ReadProcessOutput(stdout_lines);
            
            double solvertime=System.currentTimeMillis();
            
            int exitValue=RunCommand.runCommand(command, stderr_lines, rpo);
            
            solvertime=(((double) System.currentTimeMillis() - solvertime) / 1000);
            
            Stats stats=new LingelingStats(stdout_lines);
            
            // Find s line in output.
            boolean completed=false;
            boolean satisfiable=false;
            for(int i=0; i<stdout_lines.size(); i++) {
                String ln=stdout_lines.get(i);
                
                if(ln.indexOf("s ")==0) {
                    String[] words=ln.split(" ");
                    
                    if(words[1].equals("SATISFIABLE")) {
                        satisfiable=true;
                        completed=true;
                    }
                    else if(words[1].equals("UNSATISFIABLE")) {
                        satisfiable=false;
                        completed=true;
                    }
                    else if(words[1].equals("UNKNOWN")) {
                        satisfiable=false;
                        completed=false;
                    }
                }
                
                // Work around apparent bug in lingeling -- can get line 'c s UNKNOWN' on timeout or interruption.
                if(ln.indexOf("c s ")==0) {
                    String[] words=ln.split(" ");
                    if(words[2].equals("SATISFIABLE")) {
                        satisfiable=true;
                        completed=true;
                    }
                    else if(words[2].equals("UNSATISFIABLE")) {
                        satisfiable=false;
                        completed=true;
                    }
                    else if(words[2].equals("UNKNOWN")) {
                        satisfiable=false;
                        completed=false;
                    }
                }
            }
            
            if(satisfiable) {
                ArrayList<String> fileContents=new ArrayList<String>();
                
                for(int i=0; i<stdout_lines.size(); i++) {
                    String ln=stdout_lines.get(i);
                    if(ln.indexOf("v ")==0) {
                        String[] words=ln.trim().split(" ");
                        fileContents.addAll(Arrays.asList(words).subList(1, words.length));   // Leave the starting v
                    }
                }
                
                // Take off the trailing 0.
                fileContents.remove(fileContents.size()-1);
                
                return new Pair<ArrayList<String>, Stats>(fileContents, stats);
            }
            else {
                // Either unsat or not completed
                return new Pair<ArrayList<String>, Stats>(null, stats);
            }
        }
        catch(Exception e) {
            System.out.println("Exception."+e);
            CmdFlags.rmTempFiles();
            return new Pair<ArrayList<String>, Stats>(null, null);
        }
    }
}
