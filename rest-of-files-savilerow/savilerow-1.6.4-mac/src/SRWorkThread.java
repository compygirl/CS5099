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


import savilerow.expression.*;
import savilerow.treetransformer.*;
import savilerow.eprimeparser.EPrimeReader;
import savilerow.model.*;
import savilerow.solver.*;

import java.util.* ;
import java.io.* ;

// This thread does all the work. The main thread sleeps until timeout, then
// wakes and kills this thread. 

public final class SRWorkThread extends Thread {
    public void run() {
        if(CmdFlags.getMode()==CmdFlags.ReadSolution) {
            MinionSolver min=new MinionSolver();
            min.parseSolutionMode();
            return;
        }
        
        //  Read the files.
        EPrimeReader reader = new EPrimeReader(CmdFlags.eprimefile);
        Model m=reader.readModel() ;
        assert m.constraints != null;
        
        // Get the parameters
        ArrayList<ASTNode> parameters=new ArrayList<ASTNode>();
        if(CmdFlags.paramfile!=null) {
            EPrimeReader paramfile = new EPrimeReader(CmdFlags.paramfile);
            parameters=paramfile.readParameterFile(m);
        }
        if(CmdFlags.paramstring!=null) {
            EPrimeReader paramfile = new EPrimeReader(CmdFlags.paramstring, true);
            parameters=paramfile.readParameterFile(m);
        }
        
        ModelContainer mc=new ModelContainer(m, new ArrayList<ASTNode>(parameters));
        
        if(CmdFlags.dryruns) {
            //  Three dry runs.  Reset the clock after each one; if there is a timeout during a dryrun then SR will exit.
            //  For the timelimit, time starts again for each dryrun.
            
            ModelContainer mc2=mc.copy();
            mc2.dryrun();
            CmdFlags.startTime=System.currentTimeMillis();
            CmdFlags.currentModel=null;
            
            mc2=mc.copy();
            mc2.dryrun();
            CmdFlags.startTime=System.currentTimeMillis();
            CmdFlags.currentModel=null;
            
            mc2=mc.copy();
            mc2.dryrun();
            CmdFlags.startTime=System.currentTimeMillis();
            CmdFlags.currentModel=null;
        }
        
        CmdFlags.startTime=System.currentTimeMillis();
        
        mc.process();
        
        System.exit(0);  // This is needed otherwise the other thread (Main thread) will continue to 
        // sleep and SR will not exit when it has finished. 
    }
}

