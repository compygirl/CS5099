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

import savilerow.model.*;
import savilerow.expression.*;
import savilerow.CmdFlags;
import savilerow.eprimeparser.EPrimeReader;

public class MinionSolver extends Solver
{
    // minname is the name of the minion binary
    // filename is the name of the minion input file. 
    // m is the model 
    public void findSolutions(String minname, String filename, Model m) throws IOException,  InterruptedException
    {
        CmdFlags.rmTempFiles();
        
        double srtime=(((double) System.currentTimeMillis() - CmdFlags.startTime) / 1000);
        
        runMinion(minname, filename, m, false, -1);
        
        MinionStats stats=null;
        
        if((!CmdFlags.getFindAllSolutions()) && CmdFlags.getFindNumSolutions()==-1) {
            // Find one solution only. Takes the last solution because for optimisation that will be the optimal one.
            BufferedReader minsolfile=new BufferedReader(new FileReader(CmdFlags.getMinionSolsTempFile()));
            Solution sol = parseLastSolverSolution(m.global_symbols, minsolfile);
            
            stats=addStatisticsToSolution(sol, srtime);
            
            if(sol!=null || m.incumbentSolution!=null) {
                createSolutionFile( ((sol!=null)?sol:m.incumbentSolution), CmdFlags.solutionfile);
            }
        }
        else {
            // Multiple solutions. 
            BufferedReader minsolfile=new BufferedReader(new FileReader(CmdFlags.getMinionSolsTempFile()));
            parseAllSolverSolutions(m.global_symbols, minsolfile);
            
            // Do something silly here just to get 'stats' object.
            minsolfile=new BufferedReader(new FileReader(CmdFlags.getMinionSolsTempFile()));
            Solution sol = parseLastSolverSolution(m.global_symbols, minsolfile);
            stats=addStatisticsToSolution(sol, srtime);
        }
        
        // Create .info and .infor files. 
        if(stats!=null) {
            stats.makeInfoFiles();
        }
        
        CmdFlags.rmTempFiles();
    }
    
    // Given a model, returns a set of find statements with filtered domains. 
    // A special method only implemented for Minion. 
    public ArrayList<ASTNode> reduceDomains(String minname, String filename, Model m) throws IOException,  InterruptedException
    {
        ArrayList<String> lines=runMinion(minname, filename, m, true, -1);
        
        ArrayList<ASTNode> findstatements=new ArrayList<ASTNode>();
        
        // make a string to pass to the parser. 
        StringBuffer b=new StringBuffer();
        b.append("language ESSENCE' 1.0\n");
        
        
        for(int i=0; i<lines.size(); i++) {
            if(lines.get(i).length()>=4 && lines.get(i).substring(0,4).equals("find")) {
                b.append(lines.get(i));
                b.append("\n");
            }
        }
        
        b.append("such that true");
        
        EPrimeReader epr=new EPrimeReader(b.toString(), true);
        Model tmp=epr.readModel();
        
        return new ArrayList<ASTNode>(tmp.global_symbols.lettings_givens);  // actually finds, not lettings or givens. 
    }
    
    //  Create a table constraint for the given scope using Minion's bounded search. 
    public ASTNode makeTable(Model m, ArrayList<ASTNode> scope) throws IOException, InterruptedException
    {
        // Make a copy so we can change it.
        Model mcopy=m.copy();
        
        mcopy.objective=null;  // Clear objective to avoid a Minion error when objective value not set in solution.
        //   Alternative approach would be to always include the objective variable in scope...
        
        //  Move the scope vars to the top of the variable ordering.
        for(int i=scope.size()-1; i>=0; i--) {
            ASTNode var=scope.get(i);
            categoryentry c=mcopy.global_symbols.category.get(var.toString());
            
            // Remove c from the list.
            if(c.next!=null) {
                c.next.prev=c.prev;
            }
            if(c.prev!=null) {
                c.prev.next=c.next;
            }
            
            // Insert c at the head of the list. 
            c.prev=null;
            c.next=mcopy.global_symbols.category_first;
            c.next.prev=c;
            mcopy.global_symbols.category_first=c;
        }
        
        // Make the minion file.
        StringBuffer b = new StringBuffer();
        
        mcopy.toMinion(b, scope);
        
        assert CmdFlags.minionfile != null;
        
        try {
            BufferedWriter out;
            out = new BufferedWriter(new FileWriter(CmdFlags.minionfile));
            out.write(b.toString());
            out.close();
        } catch (IOException e) {
            System.out.println("Could not open file for Minion output.");
            CmdFlags.exit();
        }
        
        CmdFlags.println("Created output file for table generation " + CmdFlags.minionfile);
        
        runMinion(CmdFlags.getMinion(), CmdFlags.minionfile, mcopy, false, scope.size());
        
        // Open the Minion solution file. 
        
        BufferedReader minsolfile=new BufferedReader(new FileReader(CmdFlags.getMinionSolsTempFile()));
        ArrayList<ASTNode> table=new ArrayList<ASTNode>();
        try {
            String s=minsolfile.readLine();
            while(s!=null) {
                String[] vals=s.split("\\s");  // Split by space into individual values.
                
                ArrayList<ASTNode> tup=new ArrayList<ASTNode>();
                for(int i=0; i<vals.length; i++) {
                    tup.add(new NumberConstant(Long.valueOf(vals[i])));
                }
                
                assert tup.size()==scope.size();
                table.add(CompoundMatrix.makeCompoundMatrix(tup));
                
                s=minsolfile.readLine();
            }
        }
        catch(IOException e) {
            return null;
        }
        
        try {
            File f=new File(CmdFlags.getMinionSolsTempFile());
            f.delete();
        } catch (Exception x) {
        }
        
        return new Table(CompoundMatrix.makeCompoundMatrix(scope), CompoundMatrix.makeCompoundMatrix(table));
    }
    
    
    //  -opt-warm-start option for running Minion for a short time to get a bound on the optimisation variable. 
    //  Returns a solution.
    public Solution optWarmStart(Model m) throws IOException, InterruptedException
    {
        // Make the minion file.
        StringBuffer b = new StringBuffer();
        m.toMinion(b);
        
        try {
            BufferedWriter out;
            out = new BufferedWriter(new FileWriter(CmdFlags.minionfile));
            out.write(b.toString());
            out.close();
        } catch (IOException e) {
            System.out.println("Could not open file for Minion output.");
            CmdFlags.exit();
        }
        
        CmdFlags.println("Created output file for optimisation warm start: " + CmdFlags.minionfile);
        
        // Add the nodelimit flag for Minion
        ArrayList<String> extraflags=CmdFlags.getSolverExtraFlags();
        ArrayList<String> ef_copy=new ArrayList<String>();
        
        //  Rough heuristic of 'each variable gets 10 assignments on average'.
        ef_copy.add("-nodelimit"); ef_copy.add(String.valueOf(m.global_symbols.category.size()*10));
        
        CmdFlags.setSolverExtraFlags(ef_copy);
        
        runMinion(CmdFlags.getMinion(), CmdFlags.minionfile, m, false, -1);
        
        // Restore original solver flags. 
        CmdFlags.setSolverExtraFlags(extraflags);
        
        // Open the Minion solution file. 
        BufferedReader minsolfile=new BufferedReader(new FileReader(CmdFlags.getMinionSolsTempFile()));
        Solution sol = parseLastSolverSolution(m.global_symbols, minsolfile);
        
        return sol;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //   Private methods. 
    
    // squashDomains runs minion with -outputCompressedDomains 
    private ArrayList<String> runMinion(String minname, String filename, Model m, boolean squashDomains, int searchlim) throws IOException,  InterruptedException
    {
        if(!squashDomains) CmdFlags.runningSolver=true;  // Prevents SR's timeout from kicking in. 
        
        try
        {
            ArrayList<String> minionCommand;
            // What level of preprocess to use? 
            String proplevel;
            if(CmdFlags.getPreprocess()!=null) {
                // Use the user-specified preprocess level.
                proplevel=CmdFlags.getPreprocess();
            }
            else {
                // Either SACBounds or GAC depending on variable domain sizes. 
                proplevel=m.global_symbols.minionReduceDomainsLevel();
            }
            
            if(searchlim==-1) {
                if(squashDomains) {
                    minionCommand = new ArrayList<String>(Arrays.asList(new String[]{ minname, filename
                                                                                    , "-preprocess", proplevel
                                                                                    , "-outputCompressedDomains"
                                                                                    , "-cpulimit", "60"
                                                                                    }));
                }
                else {
                    minionCommand = new ArrayList<String>(Arrays.asList(new String[]{ minname, filename
                                                                                    , "-printsolsonly"
                                                                                    , "-preprocess", proplevel
                                                                                    , "-tableout"  , CmdFlags.getMinionStatsTempFile()
                                                                                    , "-solsout"   , CmdFlags.getMinionSolsTempFile()
                                                                                    , "-noprintsols"
                                                                                    }));
                }
            }
            else {
                // Depth-bounded search to generate table ct.
                minionCommand = new ArrayList<String>(Arrays.asList(new String[]{ minname, filename
                                                                                    , "-printsolsonly"
                                                                                    , "-tableout"  , CmdFlags.getMinionStatsTempFile()
                                                                                    , "-solsout"   , CmdFlags.getMinionSolsTempFile()
                                                                                    , "-noprintsols"
                                                                                    , "-varorder"  , "staticlimited", String.valueOf(searchlim)
                                                                                    , "-skipautoaux"
                                                                                    , "-findallsols"
                                                                                    }));
                
            }
            
            if(CmdFlags.getFindAllSolutions() && (!squashDomains) && searchlim==-1 ) {
                if(m.objective!=null) {
                    CmdFlags.println("WARNING: Ignoring -all-solutions flag because it cannot be used with optimisation.");
                    CmdFlags.setFindAllSolutions(false);
                }
                else {
                    minionCommand.add("-findallsols");
                }
            }
            
            if(CmdFlags.getFindNumSolutions()>-1 && (!squashDomains) && searchlim==-1 ) {
                if(m.objective!=null) {
                    CmdFlags.println("WARNING: Ignoring -num-solutions flag because it cannot be used with optimisation.");
                    CmdFlags.setFindNumSolutions(-1);
                }
                else {
                    minionCommand.add("-sollimit");
                    minionCommand.add(""+CmdFlags.getFindNumSolutions());
                }
            }
            
            if(!squashDomains && searchlim==-1) {
                //  if squashDomains, the extra flags could be for Gecode or something else. So don't add them. 
                minionCommand.addAll(CmdFlags.getSolverExtraFlags());
            }
            
            ArrayList<String> stdout_lines=new ArrayList<String>();
            ArrayList<String> stderr_lines=new ArrayList<String>();
            
            // Make a thread to read Minion's output
            ReadProcessOutput stdout_reader=new ReadProcessOutput(stdout_lines);
            
            int exitValue=RunCommand.runCommand(minionCommand, stderr_lines, stdout_reader);
            
            if(stderr_lines.size()!=0 || exitValue!=0) {
                CmdFlags.rmTempFiles();
            }
            return stdout_lines;
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
        try {
            String s=in.readLine();
            if(s==null) {
                return null;
            }
            ArrayList<String> solversol=new ArrayList<String>(); solversol.add(s);
            Solution sol=solverSolToAST(solversol, st);
            return sol;
        }
        catch(IOException e) {
            return null;
        }
    }
    
    Solution parseLastSolverSolution(SymbolTable st, BufferedReader in) {
        Solution sol=null;
        try {
            String lastline=null;
            while(true) {
                String s=in.readLine();
                if(s==null) {
                    if(lastline!=null) {
                        ArrayList<String> solversol=new ArrayList<String>(); solversol.add(lastline);
                        sol=solverSolToAST(solversol, st);
                    }
                    break;
                }
                lastline=s;
            }
        }
        catch(IOException e) {
            System.out.println("Could not open or parse Minion solution file. "+e);
        }
        
        return sol;
    }
    
    // Takes a solution printed out by Minion (in solution table format)
    // and turns it into a hashmap mapping variable name to value.
    HashMap<String, Long> readAllAssignments(ArrayList<String> minsol, SymbolTable st) {
        HashMap<String, Long> collect_all_values=new HashMap<String, Long>();
        
        ArrayDeque<String> minsolvals=new ArrayDeque<String>(Arrays.asList(minsol.get(0).split("\\s")));  // Split by space into individual values.
        
        categoryentry curcat=st.getCategoryFirst();
        
        while(curcat!=null) {
            String name=curcat.name;
            int category=curcat.cat;
            if(category==ASTNode.Decision) { 
                ASTNode domain=st.getDomain(name);
                
                // Try to parse the solution for 'name'
                if(domain instanceof MatrixDomain) {
                    assert false : "Internal error : matrix domain in symbol table at solver output time";
                }
                else {
                    assert domain.isFiniteSet();
                    String item=minsolvals.removeFirst();
                    long i = Long.parseLong(item.trim());
                    
                    collect_all_values.put(name, i);
                }
            }
            
            curcat=curcat.next;
        }
        
        // Last item in Minion PRINT statement is the objective.
        if(st.m!=null && st.m.objective!=null) {
            String item=minsolvals.removeFirst();
            long i = Long.parseLong(item.trim());
            collect_all_values.put(st.m.objective.getChild(0).toString(), i);
        }
        
        return collect_all_values;
    }
    
    // Parse tableout file.
    private MinionStats addStatisticsToSolution(Solution sol, double srtime) {
        MinionStats minionStats;
        try {
            minionStats = new MinionStats(CmdFlags.getMinionStatsTempFile());
            minionStats.putValue("SavileRowTotalTime", String.valueOf(srtime));
        }
        catch(Exception e1) {
            e1.printStackTrace();
            minionStats=null;
        }
        
        if(sol!=null) {
            if(minionStats!=null) {
                sol.addComment(minionStats.report("SolverNodes"));
                sol.addComment(minionStats.report("SolverTotalTime"));
                sol.addComment(minionStats.report("SolverTimeOut"));
            }
            
            sol.addComment("Savile Row TotalTime: "+srtime);
        }
        return minionStats;
    }
}
