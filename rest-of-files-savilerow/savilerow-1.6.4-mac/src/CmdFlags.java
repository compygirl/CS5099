package savilerow;
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

public final class CmdFlags {
    private static boolean verbose = false;
    
    private static String minionpath = "minion";
    private static String gecodepath = "fzn-gecode";
    private static String satsolverpath = null;   // Set a default value based on sat family. 
    private static String satfamily = "lingeling";  // for parsing output. 
    private static boolean runsolver=false;
    
    private static boolean class_trans = false;   // Dominion
    private static boolean gecode_trans = false;  // Gecode
    private static boolean minizinc_trans = false;  // Minizinc (after flattening)
    private static boolean minion_trans=false;  // Minion
    private static boolean sat_trans=false;     // SAT
    
    private static boolean use_mappers=true;
    private static boolean use_minionmappers=false;
    private static boolean use_cse=true;
    private static boolean use_ac_cse=false;
    private static boolean use_active_ac_cse=false;
    private static boolean use_active_cse=true;    // Default is -O2 with active CSE on. 
    private static boolean use_ac_cse_alt=false;
    
    private static boolean use_sat_decomp_cse=false;  // AC-CSE applied to each decomposition.
    private static boolean use_sat_alt=false;  // Alternative formulation of sums. 
    
    private static boolean make_tables=false;  // Run Minion to generate table constraints. Experimental. 
    public static ArrayList<Integer> make_tables_scope;
    
    // Optimisations that may change number of solutions.
    private static boolean use_var_symmetry_breaking=false;
    private static boolean use_eliminate_vars=false;    // Do variable elimination.  
    //  Graph colouring specific symmetry breaking. Should be generalised in time.
    private static boolean graph_col_sym_break=false;
    
    public static int accse_heuristic=1;   // Default is most occurrences first. 
    public static boolean use_aggressive_ac_cse=false;
    
    // Do dry runs to warm up the JVM
    public static boolean dryruns=false;
    
    // Extra presolving of model. 
    private static boolean use_delete_vars=true;   // Delete variables by unifying or assigning. Default is -O2 with this switched on. 
    private static boolean use_propagate=true;     // Reduce domains by running Minion with SACBounds. On by default.
    private static boolean use_propagate_extend=false;   // Extended propagate- filters aux vars as well as find vars. 
    private static boolean use_propagate_extend2=false;  // ... and tightens getBounds (in addition to above). 

    private static boolean remove_redundant_vars=false;  // remove redundant variables or not.
                                                         // Note: setting it to true will lose solutions.
                                                         // false by default
    
    private static boolean opt_warm_start=false;         //  For non-Minion output when optimising, run Minion with a small node limit to 
                                                         //  bound the optimisation variable. 
    
    private static boolean find_all_solutions=false;
    private static long find_num_solutions=-1;
    private static boolean solutions_to_stdout=false;
    private static boolean solutions_to_null=false;
    
    private static boolean use_boundvars=true;
    private static int boundvar_threshold=10000;
    
    private static boolean use_aggregate=true;  // Default -O2 with aggregation on.
    
    private static boolean test_solutions=false; // Store the model and check the solver solution satisfies all constraints. 
    
    //  Flags controlling the simplify methods. 
    
    private static boolean output_ready=false;
    private static boolean after_aggregate=false;
    
    // Modes of operation. 
    public static final int Normal=1;         // Produce an output file, plus optionally run Minion and parse the solution(s). 
    public static final int ReadSolution=2;   // Use a stored representation of the symbol table to parse a solution file.
    public static final int Multi=3;          // Exploratory reformulation. 
    
    private static int mode=Normal;
    
    private static String version="1.6.4";
    
    private static ArrayList<String> solverflags;
    
    // this should be a safe enough filename, I hope. apparently getting pid from
    // within java is a bit hairy, so I am not doing it. we can, if need be.
    private static String minionStatsTempFile = ".MINIONSTATS"
                                              + System.currentTimeMillis()
                                              + Math.abs(new Random().nextInt());
    private static String minionSolsTempFile = ".MINIONSOLS"
                                              + System.currentTimeMillis()
                                              + Math.abs(new Random().nextInt());
    
    public static volatile long startTime = System.currentTimeMillis();
    
    public static String preprocess=null;
    public static String eprimefile=null;
    public static String paramfile=null;
    public static String paramstring=null;   //   Parameter file provided on cmd line. 
    public static String solutionfile=null;
    public static String minionsolfile=null;
    public static String infofile=null;
    public static String minionfile=null;
    public static String dominionfile=null;
    public static String gecodefile=null;
    public static String minizincfile=null;
    public static String satfile=null;
    public static String auxfile=null;
    
    public static long timelimit=0;
    public static long cnflimit=0;
    public static volatile boolean runningSolver=false;
    
    public static LinkedHashMap<String, Integer> stats=new LinkedHashMap<String, Integer>();
    
    public static Model checkSolModel;  // If checking solutions, this field is used to store the model before any solver-specific encodings/transformations. 
    
    public static Model currentModel;   // Highly dangerous global variable. This field is used for the current model on the second pass --
                                        // after domain filtering, to allow getBounds and simplifiers to look up filtered domains.
                                        // Should always be null-checked before using. 
    
    public static boolean getVerbose() {
        return verbose;
    }
    
    public static void setVerbose(boolean v) {
        verbose = v;
    }
    
    public static void setMinion(String m) {
        minionpath=m;
    }
    public static String getMinion() {
        return minionpath;
    }
    
    public static void setGecode(String m) {
        gecodepath=m;
    }
    public static String getGecode() {
        return gecodepath;
    }
    
    public static void setSatSolver(String m) {
        satsolverpath=m;
    }
    public static String getSatSolver() {
        return satsolverpath;
    }
    
    // SAT family -- glucose, lingeling, minisat -- for parsing output. 
    public static void setSatFamily(String m) {
        satfamily=m;
    }
    public static String getSatFamily() {
        return satfamily;
    }
    
    public static boolean getRunSolver() {
        return runsolver;
    }
    
    public static void setRunSolver() {
        runsolver=true;
    }

    public static String getPreprocess() {
        return preprocess;
    }
    public static void setPreprocess(String s) {
        preprocess=s;
    }
    
    public static long getTimeLimit() {
        return timelimit; 
    }
    public static void setTimeLimit(long tl) {
        timelimit=tl;
    }
    public static long getCNFLimit() {
        return cnflimit; 
    }
    public static void setCNFLimit(long dl) {
        cnflimit=dl;
    }
    
    // New command line flags
    public static void setEprimeFile(String s) {
        eprimefile=s;
    }
    public static void setParamFile(String s) {
        paramfile=s;
    }
    public static void setParamString(String s) {
        paramstring=s;
    }
    public static void setMinionFile(String s) {
        minionfile=s;
    }
    public static void setSatFile(String s) {
        satfile=s;
    }
    public static void setMinionSolutionFile(String s) {
        minionsolfile=s;
    }
    public static void setSolutionFile(String s) {
        solutionfile=s;
    }
    public static void setInfoFile(String s) {
        infofile=s;
    }
    public static void setAuxFile(String s) {
        auxfile=s;
    }
    public static void setDominionFile(String s) {
        dominionfile=s;
    }
    public static void setGecodeFile(String s) {
        gecodefile=s;
    }
    public static void setMinizincFile(String s) {
        minizincfile=s;
    }
    
    public static void printlnIfVerbose(Object o) {
        if (verbose) {
            System.out.println(o);
        }
    }
    
    public static String getMinionStatsTempFile() {
        return minionStatsTempFile;
    }
    public static String getMinionSolsTempFile() {
        return minionSolsTempFile;
    }
    public static void rmTempFiles() {
        File f;
        f = new File(CmdFlags.getMinionStatsTempFile());
        if (f.exists()) f.delete();
        f = new File(CmdFlags.getMinionSolsTempFile());
        if (f.exists()) f.delete();
    }
    
    public static void setSolverExtraFlags(ArrayList<String> f) {
        for (Iterator<String> it=f.iterator(); it.hasNext();) {
            if (it.next().equals("")) {
                it.remove();   //  Strip empty strings.
            }
        }
        solverflags=f;
    }
    public static ArrayList<String> getSolverExtraFlags() {
        if(solverflags==null) {
            return new ArrayList<String>();
        }
        else {
            return solverflags;
        }
    }
    
    public static void setClasstrans() {
        class_trans = true;
    }
    
    public static boolean getClasstrans() {
        return class_trans;
    }
    
    public static void setUseDeleteVars(boolean t) {
        use_delete_vars = t;
    }
    
    public static boolean getUseDeleteVars() {
        return use_delete_vars;
    }
    
    public static void setUseEliminateVars(boolean t) {
        use_eliminate_vars = t;
    }
    
    public static boolean getUseEliminateVars() {
        return use_eliminate_vars;
    }
    
    // Output ready flag -- currently only controls simplifier in Times.  
    public static void setOutputReady(boolean b) {
        output_ready=b;
    }
    public static boolean getOutputReady() {
        return output_ready;
    }
    // After Aggregate flag -- controls removal of not-equal constraints.   
    public static void setAfterAggregate(boolean b) {
        after_aggregate=b;
    }
    public static boolean getAfterAggregate() {
        return after_aggregate;
    }
    
    public static void setUsePropagate(boolean t) {
        use_propagate = t;
    }
    
    public static boolean getUsePropagate() {
        return use_propagate;
    }
    
    public static void setUsePropagateExtend(boolean t) {
        use_propagate_extend = t;
        if(!t) use_propagate_extend2=false;
    }
    
    public static boolean getUsePropagateExtend() {
        return use_propagate_extend;
    }
    public static void setUsePropagateExtend2(boolean t) {
        use_propagate_extend2 = t;
    }
    public static boolean getUsePropagateExtend2() {
        return use_propagate_extend2;
    }

    public static void setRemoveRedundantVars(boolean t) {
        remove_redundant_vars = t;
    }
    public static boolean getRemoveRedundantVars() {
        return remove_redundant_vars;
    }

    public static void setUseAggregate(boolean t) {
        use_aggregate = t;
    }
    
    public static boolean getUseAggregate() {
        return use_aggregate;
    }
    
    public static void setUseVarSymBreaking(boolean t) {
        use_var_symmetry_breaking = t;
    }
    
    public static boolean getUseVarSymBreaking() {
        return use_var_symmetry_breaking;
    }
    
    public static void setFindAllSolutions(boolean t) {
        find_all_solutions = t;
    }
    
    public static boolean getFindAllSolutions() {
        return find_all_solutions;
    }
    
    public static void setSolutionsToStdout(boolean t) {
        solutions_to_stdout = t;
    }
    
    public static boolean getSolutionsToStdout() {
        return solutions_to_stdout;
    }
    
    public static void setSolutionsToNull(boolean t) {
        solutions_to_null = t;
    }
    
    public static boolean getSolutionsToNull() {
        return solutions_to_null;
    }
    
    public static void setFindNumSolutions(long t) {
        find_num_solutions = t;
    }
    
    public static long getFindNumSolutions() {
        return find_num_solutions;
    }
    
    public static void setUseBoundVars(boolean t) {
        use_boundvars = t;
    }
    
    public static boolean getUseBoundVars() {
        return use_boundvars;
    }
    public static int getBoundVarThreshold() {
        return boundvar_threshold;
    }
    
    public static void setGecodetrans() {
        gecode_trans = true;
    }
    
    public static boolean getGecodetrans() {
        return gecode_trans;
    }
    public static void setMiniontrans() {
        minion_trans = true;
    }
    
    public static boolean getMiniontrans() {
        return minion_trans;
    }
    public static void setMinizinctrans() {
        minizinc_trans = true;
    }
    
    public static boolean getMinizinctrans() {
        return minizinc_trans;
    }
    public static void setSattrans() {
        sat_trans = true;
    }
    public static boolean getSattrans() {
        return sat_trans;
    }
    public static void setSatAlt() {
        use_sat_alt=true;
    }
    public static boolean getSatAlt() {
        return use_sat_alt;
    }
    public static void setSatDecompCSE(boolean m) {
        use_sat_decomp_cse=m;
    }
    public static boolean getSatDecompCSE() {
        return use_sat_decomp_cse;
    }
    public static void setUseMappers(boolean m) {
        use_mappers=m;
    }
    public static boolean getUseMappers() {
        return use_mappers;
    }
    public static void setUseMinionMappers(boolean m) {
        use_minionmappers=m;
    }
    public static boolean getUseMinionMappers() {
        return use_minionmappers;
    }
    public static void setUseCSE(boolean m) {
        use_cse=m;
    }
    public static boolean getUseCSE() {
        return use_cse;
    }
    public static void setUseACCSE(boolean m) {
        use_ac_cse=m;
    }
    public static boolean getUseACCSE() {
        return use_ac_cse;
    }
    public static void setUseActiveACCSE(boolean m) {
        use_active_ac_cse=m;
        if(m) use_ac_cse=true;  // Make sure AC-CSE is switched on when switching on active AC-CSE. 
    }
    public static boolean getUseActiveACCSE() {
        return use_active_ac_cse;
    }
    public static void setUseAggressiveACCSE(boolean m) {
        use_aggressive_ac_cse=m;
    }
    public static boolean getUseAggressiveACCSE() {
        return use_aggressive_ac_cse;
    }
    public static void setUseActiveCSE(boolean m) {
        use_active_cse=m;
    }
    public static boolean getUseActiveCSE() {
        return use_active_cse;
    }
    public static void setUseACCSEAlt(boolean m) {
        use_ac_cse_alt=m;
    }
    public static boolean getUseACCSEAlt() {
        return use_ac_cse_alt;
    }
    public static int getMode() {
        return mode;
    }
    public static void setMode(int newmode) {
        mode=newmode;
    }
    public static boolean getTestSolutions() {
        return test_solutions;
    }
    public static boolean getMakeTables() {
        return make_tables;
    }
    public static boolean getOptWarmStart() {
        return opt_warm_start;
    }
    public static boolean getGraphColSymBreak() {
        return graph_col_sym_break;
    }
    // Print if not in completely silent mode.
    public static void println(Object o) {
        System.out.println(o);
    }
    
    //  Print error message to stderr and bail out. 
    public static void errorExit(String errmsg) {
        System.err.println("ERROR: "+errmsg);
        CmdFlags.exit();
    }
    public static void errorExit(String errmsg1, String errmsg2) {
        System.err.println("ERROR: "+errmsg1);
        System.err.println("ERROR: "+errmsg2);
        CmdFlags.exit();
    }
    public static void errorExit(String errmsg1, String errmsg2, String errmsg3) {
        System.err.println("ERROR: "+errmsg1);
        System.err.println("ERROR: "+errmsg2);
        System.err.println("ERROR: "+errmsg3);
        CmdFlags.exit();
    }
    
    // Exit with non-zero code. 
    public static void exit() {
        rmTempFiles();
        System.exit(1);
    }
    
    // For dominion output
    private static int ctnum=0;
    
    public static String getCtName() {
        ctnum++;
        return "con"+ctnum;
    }
    
    public static void parseArguments(String[] args) {
        ArrayList<String> arglist=new ArrayList<String>();
        // The default optimisation level is -O2 so put this on the start of the list 
        arglist.add("-O2");
        arglist.addAll(Arrays.asList(args));
        
        // First do the -O switches. The rightmost one is the one that takes
        // effect. 
        
        for(int i=0; i<arglist.size(); i++) {
            String cur=arglist.get(i);
            // Optimisation level options.
            // These (like all other command-line switches)
            // override anything specified earlier in the command line. 
            if(cur.equals("-O0") || cur.equals("-O1") || cur.equals("-O2") || cur.equals("-O3")) {
                arglist.remove(i);
                i--;
                if(cur.equals("-O0")) {
                    // Switch all optimisations off.
                    CmdFlags.setUseCSE(false);
                    CmdFlags.setUseActiveCSE(false);
                    CmdFlags.setUseACCSE(false);
                    CmdFlags.setSatDecompCSE(false);
                    CmdFlags.setUseACCSEAlt(false);
                    
                    CmdFlags.setUseDeleteVars(false);
                    CmdFlags.setUsePropagate(false);
                    CmdFlags.setUsePropagateExtend(false);
                    CmdFlags.setUseAggregate(false);
                }
                else if(cur.equals("-O1")) {
                    // Switch basic ones on. 
                    CmdFlags.setUseCSE(true);
                    CmdFlags.setUseActiveCSE(true);
                    CmdFlags.setUseACCSE(false);
                    CmdFlags.setSatDecompCSE(false);
                    CmdFlags.setUseACCSEAlt(false);
                    
                    CmdFlags.setUseDeleteVars(true);
                    CmdFlags.setUsePropagate(false);
                    CmdFlags.setUsePropagateExtend(false);
                    CmdFlags.setUseAggregate(false);
                }
                else if(cur.equals("-O2")) {
                    // Default settings.
                    CmdFlags.setUseCSE(true);
                    CmdFlags.setUseActiveCSE(true);
                    CmdFlags.setUseACCSE(false);
                    CmdFlags.setSatDecompCSE(false);
                    CmdFlags.setUseACCSEAlt(false);
                    
                    CmdFlags.setUseDeleteVars(true);
                    CmdFlags.setUsePropagate(true);
                    CmdFlags.setUsePropagateExtend(false);
                    CmdFlags.setUseAggregate(true); 
                }
                else if(cur.equals("-O3")) {
                    // Most powerful settings.
                    CmdFlags.setUseCSE(true);
                    CmdFlags.setUseActiveCSE(true);
                    CmdFlags.setUseACCSE(true);
                    CmdFlags.setSatDecompCSE(true);
                    CmdFlags.setUseACCSEAlt(false);
                    
                    CmdFlags.setUseDeleteVars(true);
                    CmdFlags.setUsePropagate(true);
                    CmdFlags.setUsePropagateExtend(false);
                    CmdFlags.setUseAggregate(true);
                }
            }
        }
        
        // Parse remaining arguments left-to-right. 
        
        while(arglist.size()>0) {
            String cur=arglist.get(0);
            arglist.remove(0);
            
            // verbose mode. 
            if(cur.equals("-v")) {
                CmdFlags.setVerbose(true);
            }
            else if(cur.equals("-nomappers")) {
                CmdFlags.setUseMappers(false);
            }
            else if(cur.equals("-minionmappers")) {
                CmdFlags.setUseMappers(false);
                CmdFlags.setUseMinionMappers(true);
            }
            
            // Reformulation options -- CSE
            else if(cur.equals("-no-cse")) {
                // Switch off all kinds of CSE.
                CmdFlags.setUseCSE(false);
                CmdFlags.setUseActiveCSE(false);
                CmdFlags.setUseACCSE(false);
                CmdFlags.setSatDecompCSE(false);
                CmdFlags.setUseACCSEAlt(false);
            }
            else if(cur.equals("-identical-cse")) {
                // Switch on identical CSE only. 
                CmdFlags.setUseCSE(true);
            }
            else if(cur.equals("-active-cse")) {
                CmdFlags.setUseActiveCSE(true);
            }
            else if(cur.equals("-ac-cse")) {
                CmdFlags.setUseACCSE(true);
                CmdFlags.setSatDecompCSE(true);
            }
            else if(cur.equals("-active-ac-cse")) {
                CmdFlags.setUseActiveACCSE(true);
            }
            else if(cur.equals("-ac-cse-aggressive")) {
                CmdFlags.setUseAggressiveACCSE(true);
            }
            else if(cur.equals("-ac-cse-heuristic")) {
                assert arglist.size()>0 : "AC-CSE heuristic integer missing";
                accse_heuristic=Integer.parseInt(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-ac-cse-alt")) {
                CmdFlags.setUseACCSEAlt(true);
            }
            
            // Model improvement. 
            
            else if(cur.equals("-deletevars")) {
                setUseDeleteVars(true);
            }
            else if(cur.equals("-aggregate")) {
                setUseAggregate(true);
            }
            else if(cur.equals("-varelim")) {
                setUseEliminateVars(true);
            }
            else if(cur.equals("-reduce-domains")) {
                setUsePropagate(true);
            }
            else if(cur.equals("-reduce-domains-extend")) {
                setUsePropagate(true);
                setUsePropagateExtend(true);
            }
            else if(cur.equals("-reduce-domains-extend2")) {
                setUsePropagateExtend2(true);  // This is broken.
            }
            else if(cur.equals("-remove-redundant-vars")) {
                setRemoveRedundantVars(true);
            }
            else if(cur.equals("-graph-col-sym-break")) {
                graph_col_sym_break=true;
            }
            else if(cur.equals("-opt-warm-start")) {
                opt_warm_start=true;
            }
            else if(cur.equals("-make-tables")) {
                make_tables=true;
                assert arglist.size()>0 : "-make-tables expects arguments specifying the scope.";
                int len=Integer.parseInt(arglist.get(0));
                arglist.remove(0);
                make_tables_scope=new ArrayList<Integer>();
                for(int i=0; i<len; i++) {
                    assert arglist.size()>0 : "-make-tables expects arguments specifying the scope.";
                    make_tables_scope.add(Integer.parseInt(arglist.get(0)));
                    arglist.remove(0);
                }
            }
            else if(cur.equals("-preprocess")) {
                assert arglist.size()>0 : ( "-preprocess expects an argument.\n"
                                             + "Options are: None, GAC, SAC, SSAC, SACBounds, SSACBounds"
                                             );
                CmdFlags.setPreprocess(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-timelimit")) {
                assert arglist.size()>0 : "-timelimit expects an argument in milliseconds.";
                CmdFlags.setTimeLimit(Integer.parseInt(arglist.get(0)));
                arglist.remove(0);
            }
            else if(cur.equals("-cnflimit")) {
                assert arglist.size()>0 : "-cnflimit expects an integer argument.";
                CmdFlags.setCNFLimit(Integer.parseInt(arglist.get(0)));
                arglist.remove(0);
            }
            
            // For experiments -- prime the virtual machine by running the translation 
            // a few times before taking a timing. 
            else if(cur.equals("-dryruns")) {
                dryruns=true;
            }
            else if(cur.equals("-test-solutions")) {
                test_solutions=true;
            }
            else if(cur.equals("-run-solver")) {
                CmdFlags.setRunSolver();
            }
            else if(cur.equals("-all-solutions")) {
                CmdFlags.setFindAllSolutions(true);
            }
            else if(cur.equals("-num-solutions")) {
                assert arglist.size()>0 : "-num-solutions expects an argument: the number of solutions required.";
                long numsols=Long.parseLong(arglist.get(0));
                if(numsols<=0) errorExit("Argument to -num-solutions is less than one.");
                CmdFlags.setFindNumSolutions(numsols);
                arglist.remove(0);
            }
            else if(cur.equals("-solutions-to-stdout")) {
                CmdFlags.setSolutionsToStdout(true);
            }
            else if(cur.equals("-solutions-to-null")) {
                CmdFlags.setSolutionsToNull(true);
            }
            else if(cur.equals("-no-bound-vars")) {
                setUseBoundVars(false);
            }
            else if(cur.equals("-var-sym-breaking")) {
                setUseVarSymBreaking(true);
            }
            
            // Outputs
            else if(cur.equals("-minion")) {
                CmdFlags.setMiniontrans();
            }
            else if(cur.equals("-gecode")) {
                CmdFlags.setGecodetrans();
            }
            else if(cur.equals("-minizinc")) {
                CmdFlags.setMinizinctrans();
            }
            // class-level transformation to Dominion input language. Will accept param files as well.
            else if(cur.equals("-dominion")) {
                CmdFlags.setClasstrans();
            }
            else if(cur.equals("-sat")) {
                CmdFlags.setSattrans();
            }
            else if(cur.equals("-sat-alt")) {
                CmdFlags.setSatAlt();
            }
            else if(cur.equals("-sat-decomp-cse")) {
                CmdFlags.setSatDecompCSE(true);
            }
            else if(cur.equals("-minion-bin")) {
                assert arglist.size()>=1 : "Missing minion executable file name" ;
                CmdFlags.setMinion(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-satsolver-bin")) {
                assert arglist.size()>=1 : "Missing SAT solver executable file name" ;
                CmdFlags.setSatSolver(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-sat-family")) {
                assert arglist.size()>=1 : "Missing SAT family" ;
                String sf=arglist.get(0);
                assert sf.equals("minisat") || sf.equals("lingeling") : "SAT family "+sf+" not supported.";
                CmdFlags.setSatFamily(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-solver-options")) {
                assert arglist.size()>0 : "Missing solver options string";
                ArrayList<String> temp=new ArrayList<String>(Arrays.asList(arglist.get(0).split(" ")));
                for(int i=0; i<temp.size(); i++) {
                    if(temp.get(i).equals("")) {
                        temp.remove(i); i--;
                    }
                }
                CmdFlags.setSolverExtraFlags(temp);
                
                arglist.remove(0);
            }
            else if(cur.equals("-gecode-bin")) {
                assert arglist.size()>=1 : "Missing Gecode executable file name" ;
                CmdFlags.setGecode(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-in-eprime")) {
                assert arglist.size()>0 : ".eprime file missing";
                
                CmdFlags.setEprimeFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-in-param")) {
                assert arglist.size()>0 : ".param file missing";
                
                CmdFlags.setParamFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-params")) {
                assert arglist.size()>0 : "parameter string missing";
                
                CmdFlags.setParamString(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-out-minion")) {
                assert arglist.size()>0 : "Minion output file name missing";
                
                CmdFlags.setMinionFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-out-sat")) {
                assert arglist.size()>0 : "SAT output file name missing";
                
                CmdFlags.setSatFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-out-minizinc")) {
                assert arglist.size()>0 : "minizinc output file name missing";
                
                CmdFlags.setMinizincFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-out-solution")) {
                assert arglist.size()>0 : "solution file name missing";
                
                CmdFlags.setSolutionFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-out-info")) {
                assert arglist.size()>0 : "statistics file name missing";
                
                CmdFlags.setInfoFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-out-aux")) {
                assert arglist.size()>0 : "aux file name missing";
                
                CmdFlags.setAuxFile(arglist.get(0));
                arglist.remove(0);
            }
            
            else if(cur.equals("-minion-sol-file")) {
                assert arglist.size()>0 : "missing Minion solution file name";
                
                CmdFlags.setMinionSolutionFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-out-dominion")) {
                assert arglist.size()>0 : "Missing Dominion output file name";
                
                CmdFlags.setDominionFile(arglist.get(0));
                arglist.remove(0);
            }
            else if(cur.equals("-out-gecode")) {
                assert arglist.size()>0 : "Missing Gecode output file name" ;
                
                CmdFlags.setGecodeFile(arglist.get(0));
                arglist.remove(0);
            }
            
            else if(cur.equals("-mode")) {
                assert arglist.size()>0 : "Missing mode argument" ;
                
                String mode_st=arglist.get(0);
                
                if(mode_st.equals("Normal")) {
                    setMode(Normal);
                }
                else if(mode_st.equals("ReadSolution")) {
                    setMode(ReadSolution);
                }
                else if(mode_st.equals("Multi")) {
                    setMode(Multi);
                }
                else {
                    CmdFlags.errorExit("-mode argument not followed by Normal, ReadSolution or Multi.");
                }
                
                arglist.remove(0);
            }
            
            // If a parameter is not parsed by any of the above cases,
            // try to guess whether it is the .eprime file or the .param file. 
            else if(cur.length()>=7 && cur.substring(cur.length()-7, cur.length()).equals(".eprime") && CmdFlags.eprimefile==null) {
                CmdFlags.eprimefile=cur;
            }
            
            else if(cur.length()>=6 && cur.substring(cur.length()-6, cur.length()).equals(".param") && CmdFlags.paramfile==null) {
                CmdFlags.paramfile=cur;
            }
            
            else {
                System.err.println("ERROR: Failed to parse the following argument: "+cur);
                System.err.println("ERROR: Remaining arguments: "+arglist);
                CmdFlags.printUsage();
                CmdFlags.exit();
            }
        }
        
        //////////////////////////////////////////////////////////////////////////////////////////////////////
        //
        //   Finished parsing command-line arguments. Now do some checks. 
        
        
        //  Different checks for different modes. 
        if(getMode()==Normal || getMode()==Multi) {
            if(CmdFlags.eprimefile==null) {
                CmdFlags.println("ERROR: Not given Essence' model file.");
                CmdFlags.printUsage();
                CmdFlags.exit();
            }
            
            // defaults for minion and dominion output files, and solution file name.
            if(CmdFlags.dominionfile==null) {
                String tmp=CmdFlags.eprimefile;
                if(tmp.substring(tmp.length()-7, tmp.length()).equals(".eprime")) {
                    tmp=tmp.substring(0,tmp.length()-7);
                }
                CmdFlags.dominionfile=tmp+".dominion";
            }
            if(CmdFlags.minionfile==null) {
                if(CmdFlags.paramfile!=null)
                    CmdFlags.minionfile=CmdFlags.paramfile+".minion";
                else
                    CmdFlags.minionfile=CmdFlags.eprimefile+".minion";
            }
            if(CmdFlags.satfile==null) {
                if(CmdFlags.paramfile!=null)
                    CmdFlags.satfile=CmdFlags.paramfile+".dimacs";
                else
                    CmdFlags.satfile=CmdFlags.eprimefile+".dimacs";
            }
            if(CmdFlags.auxfile==null) {
                if(CmdFlags.paramfile!=null)
                    CmdFlags.auxfile=CmdFlags.paramfile+".aux";
                else
                    CmdFlags.auxfile=CmdFlags.eprimefile+".aux";
            }
            if(CmdFlags.gecodefile==null) {
                if(CmdFlags.paramfile!=null)
                    CmdFlags.gecodefile=CmdFlags.paramfile+".fzn";
                else
                    CmdFlags.gecodefile=CmdFlags.eprimefile+".fzn";
            }
            if(CmdFlags.solutionfile==null) {
                if(CmdFlags.paramfile!=null)
                    CmdFlags.solutionfile=CmdFlags.paramfile+".solution";
                else
                    CmdFlags.solutionfile=CmdFlags.eprimefile+".solution";
            }
            if(infofile==null) {
                if(paramfile!=null)
                    infofile=paramfile+".info";
                else
                    infofile=eprimefile+".info";
            }
            if(CmdFlags.minizincfile==null) {
                if(CmdFlags.paramfile!=null)
                    CmdFlags.minizincfile=CmdFlags.paramfile+".mzn";
                else
                    CmdFlags.minizincfile=CmdFlags.eprimefile+".mzn";
            }
            if(satsolverpath==null) {
                satsolverpath=satfamily;  // Default value of sat solver is minisat or lingeling.  
            }
            // Default Minion output
            if(!getMinizinctrans() && !getGecodetrans() && !getClasstrans()) {
                setMiniontrans();
            }
        }
        else if(getMode()==ReadSolution) {
            // At the moment just for minion -- so did the user specify the minion file and the solution table file?
            if(CmdFlags.auxfile==null) {
                CmdFlags.errorExit("When using ReadSolution mode, -out-aux must be used to specify the .aux file.");
            }
            
            if(CmdFlags.solutionfile == null) {
                CmdFlags.errorExit("When using ReadSolution mode, -out-solution must be used to specify the name for the Essence' solution file(s).");
            }
            
            if(CmdFlags.minionsolfile==null) {
                CmdFlags.errorExit("When using ReadSolution mode, -minion-sol-file must be used to specify the name of the Minion solution table file.");
            }
        }
        else {
            // What happened to the mode? Should be redundant, but doesn't hurt.
            assert false : "Mode not recognised" ;
        }
    }
    
    public static void printUsage() {
        System.out.println(
        "Savile Row " + version + " (Repository Version: " + RepositoryVersion.repositoryVersion + ")\n"
        +"Specifying the input files (for Normal mode):\n"
        +"\n"
        +"  -in-eprime <filename>          :   Specifies file name for Essence' constraint model file.\n"
        +"                                     If the file name ends with .eprime, it may be used directly\n"
        +"                                     on the command line without the -in-eprime flag.\n"
        +"\n"
        +"  -in-param <filename>           :   Specifies file name for Essence' parameter file\n"
        +"                                     If the file name ends with .param, it may be used directly\n"
        +"                                     on the command line without the -in-param flag.\n"
        +"\n"
        +"  -params <string>               :   Specifies Essence' parameters on the command line.\n"
        +"                                     The format of <string> is the same as for a parameter file (where,\n"
        +"                                     incidentally, the language line is optional so a parameter file can\n"
        +"                                     contain only letting statements). \n"
        +"                                     The string should contain statements \"letting identifier be value\"\n"
        +"                                     or \"letting identifier=value\". \n"
        +"                                     For example: \n"
        +"                                     -params \"letting n_nurses=4 letting Demand=[[1,0,1,0],[0,2,1,0]]\"\n"
        +"\n"
        +"Specifying output format (for Normal mode):\n"
        +"\n"
        +"  -minion  (default)             :   Create output in Minion 3 format.\n"
        +"                                     This format is flat for numerical expressions and non-flat for And and Or since\n"
        +"                                     these are implemented using watched-and and watched-or metaconstraints.\n"
        +"\n"
        +"  -gecode                        :   Create output in Flatzinc suitable for Gecode's fzn-gecode tool.\n"
        +"                                     This uses the same translation pipeline as Minion output as far as possible.\n"
        +"                                     The main differences are that the model is entirely flat and some constraints\n"
        +"                                     are rewritten where required for reification.\n"
        +"\n"
        +"  -minizinc                      :   Create output in a partly flat, instance-level subset of the Minizinc language.\n"
        +"                                     This allows access via mzn2fzn to several solvers.\n"
        +"                                     The output is as close as possible to the Minion 3 output.\n"
        +"\n"
        +"  -sat                           :   Create SAT output in DIMACS format for use by a SAT solver.\n"
        +"\n"
        +"  -dominion                      :   Create output in the Dominion Input Language (experimental)\n"
        +"                                     Perform class-level flattening if necessary (i.e. if some parameters are\n"
        +"                                     not instantiated) and produce output in the Dominion Input Language.\n"  
        +"                                     This uses a separate translation pipeline from Minion, Gecode and Minizinc output.\n"
        +"\n"
        +"Output file options (each has a default value so none of these are required):\n"
        +"\n"
        +"  -out-minion <filename>         :   Specifies file name for Minion output.\n"
        +"  -out-gecode <filename>         :   Specifies file name for Gecode (flatzinc) output.\n"
        +"  -out-minizinc <filename>       :   Specifies file name for Minizinc output.\n"
        +"  -out-sat <filename>            :   Specifies file name for SAT (DIMACS) output.\n"
        +"  -out-dominion <filename>       :   Specifies file name for Dominion output.\n"
        +"\n"
        +"  -out-solution <filename>       :   Specifies file name for output of the solution if Savile Row runs a solver and\n"
        +"                                     parses the solver's output.\n"
        +"  -out-info <filename>           :   Specifies file name for output of info file (statistics from Savile Row and Minion).\n"
        +"  -out-aux <filename>            :   Specifies file name for the .aux file containing the symbol table.\n"
        +"                                     The .aux file is required for ReadSolution mode.\n"
        +"\n"
        +"Optimisation levels:\n"
        +"\n"
        +"  -O0                            :   Optimisation level 0. Switches off all optional optimisations, however Savile Row\n"
        +"                                     will still simplify expressions. For example, the boolean expression\n"
        +"                                     3+4=10 would simplify to false.\n"
        +"  -O1                            :   Optimisation level 1. Applies optimisations that are very efficient in both space and time.\n"
        +"                                     Currently -O1 is equivalent to -active-cse and -deletevars.\n"
        +"  -O2  (default)                 :   Optimisation level 2. Applies a generally recommended set of optimisations.\n"
        +"                                     Currently -O2 is equivalent to -active-cse, -deletevars, -reduce-domains and -aggregate.\n"
        +"                                     Optimisation level 2 requires Minion to be available to filter variable domains.\n"
        +"  -O3                            :   Optimisation level 3. Applies most available optimisations.\n"
        +"                                     Currently -O3 is equivalent to -active-cse, -deletevars, -reduce-domains, \n"
        +"                                     -ac-cse and -aggregate.\n"
        +"\n"
        +"Translation Options:\n"
        +"\n"
        +"  -no-cse                        :   Switches off all common subexpression elimination (CSE).\n"
        +"  -identical-cse                 :   Switches on Identical CSE. This matches identical subexpressions only.\n"
        +"  -ac-cse                        :   Switches on Associative-Commutative CSE (AC-CSE) for the operators And (/\\), Or (\\/),\n"
        +"                                     Product and Sum. AC-CSE exploits associativity and commutativity to reveal common\n"
        +"                                     subexpressions.\n"
        +"  -active-cse                    :   Switches on Active CSE. May be used with the option above.\n"
        +"                                     Active CSE attempts to match expressions that are semantically equivalent after some\n"
        +"                                     simple transformation such as Boolean negation.\n"
        +"  -active-ac-cse                 :   Switches on Active AC-CSE. This extends AC-CSE on sums by matching subexpressions where\n"
        +"                                     one subexpression is the negation of another. For example it able to extract \n"
        +"                                     x+y from the expressions x+y+z, w-x-y and 10-x-y.\n"
        +"                                     Active AC-CSE is identical to AC-CSE on And, Or and Product.\n"
        +"  -deletevars                    :   Switch on variable deletion for variables that are equal to a constant or equal to\n"
        +"                                     another decision variable.\n"
        +"  -reduce-domains                :   This option filters the domains of the 'find' decision variables. This option only functions\n" 
        +"                                     when Minion is available. It calls Minion to perform SACBounds preprocessing, a\n"
        +"                                     restricted form of SAC where the SAC test is applied to the upper and lower bound\n"
        +"                                     of each variable.\n"
        +"  -reduce-domains-extend         :   An extension of -reduce-domains that filters the domains of auxiliary variables as\n"
        +"                                     well as 'find' variables.\n"
        +"  -aggregate                     :   Collect constraints into global constraints where possible. Currently performs two\n"
        +"                                     types of aggregation: constructing AllDifferent constraints from not-equal, less-than\n"
        +"                                     and shorter AllDifferents, and constructing GCC from atleast and atmost.\n"
        +"  -nomappers                     :   When translating to Dominion, do not use mappers (views).\n"
        +"  -minionmappers                 :   When translating to Dominion, only use mappers when a comparable mapped constraint is\n"
        +"                                     also available in Minion (e.g. multiplication mappers inside a sum constraint are\n"
        +"                                     allowed because Minion contains a weighted sum constraint).\n"
        +"  -no-bound-vars                 :   When translating to Minion, never use Minion's BOUND type variables. Instead always use\n"
        +"                                     DISCRETE even for very large domains. The default behaviour is to use DISCRETE for domains\n"
        +"                                     of size less than or equal to 10,000, and BOUND when the domain is larger than 10,000.\n"
        +"                                     Using BOUND type variables can reduce the level of consistency enforced for some\n"
        +"                                     constraints.\n"
        +"  -remove-redundant-vars         :   Remove redundant vars by adding a constraint assigning any variables that are not\n"
        +"                                     mentioned in any existing constraint or the objective function.\n"
        +"                                     It is off by default.\n"
        +"                                     Note that enabling this flag may lose solutions.\n"
        +"\n"
        +"Controlling Savile Row:\n"
        +"  -timelimit <time>              :   Specifies a time limit in milliseconds. Savile Row will stop when the time limit is\n"
        +"                                     reached, unless it has completed the translation and is running a solver to find a\n" 
        +"                                     solution. The time measured is wallclock time not CPU time.\n"
        +"                                     To apply a time limit to Minion as well as Savile Row, use\n" 
        +"                                     -solver-options \"-cpulimit <time>\"\n"
        +"  -cnflimit <size>               :   Limit the number of clauses in the SAT output file to a maximum of <size>.\n"
        +"\n"
        +"Solver control:\n"
        +"  -run-solver                    :   Run the selected solver and parse its output. Currently implemented for\n"
        +"                                     Minion and Gecode.\n"
        +"  -all-solutions                 :   Find all solutions. These are output in a sequence of numbered files,\n"
        +"                                     for example, nurses.param.solution.000001 to nurses.param.solution.000871\n"
        +"                                     This option cannot be used on optimisation problems.\n"
        +"  -num-solutions <n>             :   Find <n> solutions. These are output in a sequence of numbered files,\n"
        +"                                     for example, nurses.param.solution.000001 to nurses.param.solution.000050\n"
        +"                                     This option cannot be used on optimisation problems.\n"
        +"  -solutions-to-stdout           :   Instead of writing solutions to files, send them to stdout separated by\n"
        +"                                     a line of 10 minus signs.\n"
        +"  -solutions-to-null             :   Do not output solutions in any way.\n"
        +"  -solver-options <string>       :   Pass through some additional command-line options to the solver.\n"
        +"\n"
        +"Solver control -- Minion:\n"
        +"  -minion-bin <filename>         :   Tell Savile Row where the Minion binary is. Default is to use the Minion binary\n"
        +"                                     included in the Savile Row distribution.\n"
        +"  -preprocess                    :   Specifies preprocessing option to be passed to Minion both for solving (when\n"
        +"                                     using -run-solver) and for domain filtering (when using -reduce-domains or -O2\n" 
        +"                                     or higher).\n"
        +"                                     Possible values: None, GAC, SAC, SSAC, SACBounds, SSACBounds.\n"
        +"                                     By default GAC will be used when there are large variable domains, otherwise\n"
        +"                                     SACBounds will be used.\n"
        +"\n"
        +"Solver control -- Gecode:\n"
        +"  -gecode-bin <filename>         :   Tell Savile Row the command to run the Gecode Flatzinc binary (default is \"fzn-gecode\").\n"
        +"\n"
        +"Solver control -- SAT solver:\n"
        +"  -sat-family <name>             :   Specify the family of the SAT solver (one of \"minisat\", \"lingeling\").\n"
        +"                                     Allows Savile Row to parse the output of the solver when using the -run-solver flag.\n" 
        +"                                     Default is \"lingeling\".\n"
        +"  -satsolver-bin <filename>      :   Tell Savile Row where the SAT solver binary is.\n"
        +"                                     Default is \"lingeling\" when the SAT family is lingeling, and \"minisat\" otherwise.\n"
        +"\n"
        +"Specifying mode of operation:\n"
        +"  -mode [Normal | ReadSolution]  :   Normal mode reads an Essence' model file and optional parameter file\n"
        +"                                     and produces output for a constraint solver. It may also run a solver\n"
        +"                                     and parse the solutions.\n"
        +"                                     ReadSolution mode takes a solution table file created by Minion and\n"
        +"                                     produces an Essence' solution file. If the solution table file contains\n"
        +"                                     multiple solutions, the flag -all-solutions can be used to parse them all,\n"
        +"                                     or the flag -num-solutions <n> may be used to parse the first n solutions.\n"
        +"  -minion-sol-file <filename>    :   Specifies the solution table file (produced by Minion's -solsout flag).\n"
        +"                                     In ReadSolution mode, the flags -out-aux and -out-solution are required,\n"
        +"                                     the first so that Savile Row can load its symbol table that was saved when\n"
        +"                                     translating the problem instance, and the second to specify where to write\n"
        +"                                     the solutions. The flags -all-solutions and -num-solutions are optional in\n"
        +"                                     ReadSolution mode.\n"
        +"\n"
        +"Examples:\n"
        +"\n"
        +"    ./savilerow examples/sudoku/sudoku.eprime examples/sudoku/sudoku.param -run-solver\n"
        +"    Runs Savile Row on the Sudoku puzzle example, with default optimisation (-O2) and\n"
        +"    the default solver (Minion). Minion is called twice, first to filter the variable\n"
        +"    domains and second to solve the problem instance.\n"
        +"\n"
        +"    ./savilerow examples/carSequencing/carSequencing.eprime examples/carSequencing/carSequencing10.param -O3 -sat -run-solver\n"
        +"    Runs Savile Row on an instance of car sequencing, using the highest level of\n"
        +"    optimisation (-O3) and targeting SAT. No SAT solver is specified so the default\n"
        +"    (lingeling) will be used. Requires the 'lingeling' SAT solver binary to be in\n"
        +"    the path.\n"
        );
    }
}
