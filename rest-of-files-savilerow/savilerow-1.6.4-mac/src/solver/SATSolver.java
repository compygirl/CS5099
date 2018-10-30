
package savilerow.solver;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Patrick Spracklen and Peter Nightingale
    
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

public abstract class SATSolver extends Solver
{
    static int solutionNumber=1;
    private ArrayList<String> stdout_lines=new ArrayList<String>();
    
    Model m;
    public SATSolver(Model _m) {
        m=_m;
    }
    
    // satSolverName is the name of the SAT solver binary
    // filename is the name of the CNF file. 
    // m is the model 
    public void findSolutions(String satSolverName, String fileName, Model m) throws IOException,  InterruptedException
    {
        //If there is an objective (maximising/minimising)
        if (m.objective!=null) {
            findObjective(satSolverName,fileName, m);
        }
        
        //Check if all solutions are required
        else if(CmdFlags.getFindAllSolutions() || CmdFlags.getFindNumSolutions()>-1) {
            findMultipleSolutions(satSolverName,fileName,m);
        }
        else {
            findOneSolution(satSolverName, fileName, m);
        }
    }
    
    // Instantiated for different SAT solver classes. 
    public abstract Pair<ArrayList<String>, Stats> runSatSolver(String satSolverName, String filename, Model m) throws IOException,  InterruptedException;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //   Private methods. 
    
    // Method which adds the negation of the current solution to the dimacs file. Used 
    // when multiple solutions are required.
    public void findMultipleSolutions(String satSolverName, String fileName, Model m) {
        double srtime=(((double) System.currentTimeMillis() - CmdFlags.startTime) / 1000);
        
        int numdigits=6;
        
        try{
            long solutionCounter=0;
            
            Stats totalstats=null;
            
            while(true)
            {
                if( !(CmdFlags.getFindAllSolutions()) && solutionCounter>=CmdFlags.getFindNumSolutions()) {
                    // Finished.
                    break;
                }
                
                Pair<ArrayList<String>, Stats> p=runSatSolver(satSolverName,fileName, m);
                
                ArrayList<String> currentSolution=p.getFirst();
                Stats stats=p.getSecond();
                
                if(totalstats==null) {
                    totalstats=stats;
                }
                else {
                    totalstats=totalstats.add(stats);
                }
                
                if(currentSolution==null) {
                    break;
                }
                
                Solution sol=solverSolToAST(currentSolution, m.global_symbols);
                
                String fmtint=String.format("%0"+numdigits+"d",solutionCounter+1);
                
                createSolutionFile(sol, CmdFlags.solutionfile+"."+fmtint);
                
                solutionCounter++;
                
                //Get the assignment of variables returned by Minisat
                StringBuffer clauseToAdd=new StringBuffer();
                
                //  Run through all the variables and flip their assignments
                //  MUCH longer clause than necessary.
                for(int i=0; i<currentSolution.size(); i++)   
                {
                    //If the current variable was assigned false switch to true
                    if (currentSolution.get(i).contains("-"))
                        currentSolution.set(i, currentSolution.get(i).split("-")[1]);
                    else
                        currentSolution.set(i, "-"+currentSolution.get(i));
                    
                    clauseToAdd.append(currentSolution.get(i));
                    clauseToAdd.append(" ");
                }
                
                m.satModel.addClauseAfterFinalise(clauseToAdd.toString());
            }
            totalstats.putValue("SavileRowTotalTime", String.valueOf(srtime));
            writeToFileSolutionStats(totalstats);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    // Method to find an objective (minimising/maximising problem)
    public void findObjective(String satSolverName, String fileName, Model m)
    {
        double srtime=(((double) System.currentTimeMillis() - CmdFlags.startTime) / 1000);
        
        //Get the ASTNode that is constrained by the objective
        Identifier objectiveNode=(Identifier) m.objective.getChild(0);
        //Get the domain of the objective variable
        ArrayList<Intpair> domain=objectiveNode.global_symbols.getDomain(objectiveNode.getName()).getIntervalSet();
        
        ArrayList<String> currentSolution=null;
        int solutionNumber=0;
        
        Stats totalstats=null;
        
        try {
            while(true) {
                Pair<ArrayList<String>, Stats> p=runSatSolver(satSolverName,fileName, m);
                
                if(p.getFirst()!=null) currentSolution=p.getFirst();
                Stats stats=p.getSecond();
                
                if(totalstats==null) {
                    totalstats=stats;
                }
                else {
                    totalstats=totalstats.add(stats);
                }
                
                if(p.getFirst()==null) {
                    break;
                }
                
                // Get the assignments to SR variables.
                HashMap<String, Long> sol=readAllAssignments(currentSolution, m.global_symbols);
                
                Long objectiveValue=sol.get(m.objective.getChild(0).toString());
                
                System.out.println("While optimising, found value: "+objectiveValue);
                
                String clauseToAdd;
                
                // Construct the new clause.
                if (m.objective instanceof Minimising)
                {
                    long value=m.satModel.getOrderVariable(objectiveNode.getName(), objectiveValue-1);
                    clauseToAdd=String.valueOf(value);
                }
                else {
                    long value=m.satModel.getOrderVariable(objectiveNode.getName(), objectiveValue);
                    clauseToAdd=String.valueOf(-value);
                }
                
                // Write the clause to the CNF file
                m.satModel.addClauseAfterFinalise(clauseToAdd);
                
                solutionNumber++;
            }
            
            if(solutionNumber>0)
            {
                Solution sol=solverSolToAST(currentSolution, m.global_symbols);
                
                createSolutionFile(sol, CmdFlags.solutionfile);
            }
            else if(m.incumbentSolution!=null) {
                createSolutionFile(m.incumbentSolution, CmdFlags.solutionfile);
            }
            totalstats.putValue("SavileRowTotalTime", String.valueOf(srtime));
            writeToFileSolutionStats(totalstats);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    // Find a single solution, if there is one. 
    public void findOneSolution(String satSolverName, String fileName, Model m)
    {
        double srtime=(((double) System.currentTimeMillis() - CmdFlags.startTime) / 1000);
        
        try {
            Pair<ArrayList<String>, Stats> p=runSatSolver(satSolverName,fileName, m);
            
            ArrayList<String> currentSolution=p.getFirst();
            Stats stats=p.getSecond();
            stats.putValue("SavileRowTotalTime", String.valueOf(srtime));
            
            writeToFileSolutionStats(stats);
            
            //Get the assignment of variables returned by the sat solver.
            if(currentSolution!=null) {
                Solution sol=solverSolToAST(currentSolution, m.global_symbols);
                createSolutionFile(sol, CmdFlags.solutionfile);
            }
            else if(m.incumbentSolution!=null) {   // In a case where the objective was dropped. 
                createSolutionFile(m.incumbentSolution, CmdFlags.solutionfile);
            }
            else {
                System.out.println("No solution found.");
            }
            
            CmdFlags.rmTempFiles();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    // To be used when parsing all/multiple solutions.
    Solution parseOneSolverSolution(SymbolTable st, BufferedReader in) {
        return null;
    }
    
    Solution parseLastSolverSolution(SymbolTable st, BufferedReader in) {
        return null;
    }
    
    private void writeToFileSolutionStats(Stats stats) {
        // Add the number of SAT variables and SAT clauses.
        stats.putValue("SATVars", String.valueOf(m.satModel.getNumVars()));
        stats.putValue("SATClauses", String.valueOf(m.satModel.getNumClauses()));
        
        // Create .info and .infor files. 
        if(stats!=null) {
            stats.makeInfoFiles();
        }
    }
    
    
    
    // Takes a solution as SAT literals
    // and turns it into a hashmap mapping variable name to value.
    HashMap<String, Long> readAllAssignments(ArrayList<String> satSol, SymbolTable st) {
        HashMap<String, Long> collect_all_values=new HashMap<String, Long>();
        
        Sat satModel=st.m.satModel;
        
        long assignprev=0;
        for(int i=0; i<satSol.size(); i++) {
            long assign=Long.valueOf(satSol.get(i));
            
            //  Direct encoding
            NumberMap n = satModel.getDimacsMapping(assign);
            
            if(n!=null) {
                collect_all_values.put(n.getVariable(), n.getValue());
                assignprev=assign;
                continue;
            }
            
            // Order encoding -- for variables that have only order encoding.
            // Solvers MUST output literals in order for this to work. 
            
            // There are three cases. For the first var in the order enc, i.e. [x<=min(D(x))], 
            // if the literal is positive we find it in orderMappingMin to get the SR variable and value.
            
            if(assign>0) {
                n=satModel.orderMappingMin.get(assign);
                if(n!=null) {
                    collect_all_values.put(n.getVariable(), n.getValue());
                    assignprev=assign;
                    continue;
                }
            }
            
            // For other vars in the order enc, if the var is positive and its
            // prececessor is negative, then we can find it in orderMappingMid.
            
            if(assign>0 && assignprev<0) {
                n=satModel.orderMappingMid.get(assign);
                if(n!=null) {
                    collect_all_values.put(n.getVariable(), n.getValue());
                    assignprev=assign;
                    continue;
                }
            }
            
            // For the top value in the domain, there is no [x<=max(D(x))] SAT variable
            // The topmost SAT variable must be false in this case so we use that.
            
            if(assign<0) {
                n=satModel.orderMappingMax.get(assign);
                if(n!=null) {
                    collect_all_values.put(n.getVariable(), n.getValue());
                    assignprev=assign;
                    continue;
                }
            }
            
            assignprev=assign;  // Store for next iteration
        }
        
        // Bit of a hack -- should be somewhere else.
        if(CmdFlags.getTestSolutions()) {
            checkSolution(collect_all_values);
        }
        
        return collect_all_values;
    }
}
