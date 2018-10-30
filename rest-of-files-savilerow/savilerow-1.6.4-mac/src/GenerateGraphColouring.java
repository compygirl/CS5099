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

// Simple class with main method to read and translate Essence'.

public final class GenerateGraphColouring {

  /* ====================================================================
     main for testing
    ==================================================================== */ 
    public static void main(String[] args) {
        // Parse the command-line arguments
        
        String paramfile=args[0];
        
        // read the solution
        EPrimeReader reader = new EPrimeReader(paramfile);
        
        Model m=new Model(new BooleanConstant(true), new SymbolTable(), null, null, null);
        
        ArrayList<ASTNode> params=reader.readParameterFile(m);
        
        long colours=-1;
        
        long vertices=-1;
        long edge_count=-1;
        
        ASTNode edges=null;
        
        for(ASTNode param : params) {
            if(param.getChild(0).toString().equals("colours")) {
                colours=param.getChild(1).getValue();
            }
            
            if(param.getChild(0).toString().equals("vertices")) {
                vertices=param.getChild(1).getValue();
            }
            
            if(param.getChild(0).toString().equals("edge_count")) {
                edge_count=param.getChild(1).getValue();
            }
            
            if(param.getChild(0).toString().equals("edges")) {
                edges=param.getChild(1);
            }
        }
        
        for(int i=0; i<vertices; i++) {
            String nam="vertex"+i;
            
            m.global_symbols.newVariable(nam, new IntegerDomain(new Range(new NumberConstant(1), new NumberConstant(colours))), ASTNode.Decision);
        }
        
        m.global_symbols.newVariable("minvar", new IntegerDomain(new Range(new NumberConstant(1), new NumberConstant(colours))), ASTNode.Decision);
        
        ArrayList<ASTNode> clist=new ArrayList<ASTNode>();
        for(int i=1; i<edges.numChildren(); i++) {
            clist.add(new AllDifferent(new Identifier("vertex"+edges.getChild(i).getChild(1).getValue(), m.global_symbols), 
                                       new Identifier("vertex"+edges.getChild(i).getChild(2).getValue(), m.global_symbols)));
        }
        
        for(int i=0; i<vertices; i++) {
            clist.add(new LessEqual(new Identifier("vertex"+i, m.global_symbols), new Identifier("minvar", m.global_symbols)));
        }
        
        m.constraints=new And(clist);
        
        System.out.println(m);
        
        
    }
    
}
