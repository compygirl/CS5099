package savilerow.treetransformer;
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
import savilerow.model.*;

import java.util.*;

// Replaceable container that holds potential CSEs 
// This one only matches only identical expressions with identical quantification.
// (identical after renaming quantifier id's to a common nameset)

class CSEContainerClassIdentical extends CSEContainerClass
{
    Model m;
    private HashMap<String, ASTNode> exps;    // retrofit CSE onto class-level flattening. May not do things in the right order
    // to get the best CSEs.
    
    public CSEContainerClassIdentical(Model _m) {
        m=_m;
        exps=new HashMap<String, ASTNode>();
    }
    
    // q_id and qdoms is ordered by quantifier order.
	public void storePotentialCSE(ASTNode curnode, ArrayList<ASTNode> q_id_ordered, ArrayList<ASTNode> qdoms_ordered, ArrayList<ASTNode> conditions, ASTNode auxvar) {
	    // Put everything into a container
	    ArrayList<ASTNode> temp=new ArrayList<ASTNode>();
        temp.add(curnode);
        temp.add(new Container(qdoms_ordered)); temp.add(new Container(conditions));
        ASTNode hasher=new Container(temp);
        
        for(int i=0; i<q_id_ordered.size(); i++) {
            ASTNode id=q_id_ordered.get(i);
            ASTNode rename=new Identifier("ydJDFpqbnGjkzzyu_"+i, m.global_symbols);
            ReplaceASTNode r=new ReplaceASTNode(id, rename);
            
            hasher=r.transform(hasher);
            auxvar=r.transform(auxvar);
        }
        System.out.println("CSE INSERT INTO HASHTABLE: "+hasher.toString());
        exps.put(hasher.toString(), auxvar);
	}
	
	public ASTNode lookupCSE(ASTNode curnode, ArrayList<ASTNode> q_id_ordered, ArrayList<ASTNode> qdoms_ordered, ArrayList<ASTNode> conditions) {
	    // Put everything into a container
	    ArrayList<ASTNode> temp=new ArrayList<ASTNode>();
        temp.add(curnode);
        temp.add(new Container(qdoms_ordered)); temp.add(new Container(conditions));
        ASTNode hasher=new Container(temp);
        
        for(int i=0; i<q_id_ordered.size(); i++) {
            ASTNode id=q_id_ordered.get(i);
            ASTNode rename=new Identifier("ydJDFpqbnGjkzzyu_"+i, m.global_symbols);
            ReplaceASTNode r=new ReplaceASTNode(id, rename);
            
            hasher=r.transform(hasher);
        }
        
        ASTNode auxvar=exps.get(hasher.toString());
        
        if(auxvar!=null) {
            auxvar=auxvar.copy();
            for(int i=0; i<q_id_ordered.size(); i++) {
                ASTNode id=q_id_ordered.get(i);
                ASTNode rename=new Identifier("ydJDFpqbnGjkzzyu_"+i, m.global_symbols);
                ReplaceASTNode r=new ReplaceASTNode(rename, id);
                
                auxvar=r.transform(auxvar);
            }
        }
        
        return auxvar;
	}
	
}
