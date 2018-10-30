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

// Matches shifted expressions e.g.   M[i+1]=i+1  should match with M[i]=i , given similar quantification of i. 
// Still requires quantification to be identical after shifting.

class CSEContainerClassShift extends CSEContainerClass
{
    Model m;
    private HashMap<String, ASTNode> exps;    // retrofit CSE onto class-level flattening. May not do things in the right order
    // to get the best CSEs.
    
    public CSEContainerClassShift(Model _m) {
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
        
        ArrayList<ASTNode> renamed_q_id_ordered=new ArrayList<ASTNode>();
        
        for(int i=0; i<q_id_ordered.size(); i++) {
            ASTNode id=q_id_ordered.get(i);
            ASTNode rename=new Identifier("ydJDFpqbnGjkzzyu_"+i, m.global_symbols);
            renamed_q_id_ordered.add(rename);
            ReplaceASTNode r=new ReplaceASTNode(id, rename);
            
            hasher=r.transform(hasher);
        }
        
        // Call procedure to do shifts.
        ArrayList<ASTNode> hashers=new ArrayList<ASTNode>();
        shifty_looking_geezer(hashers, renamed_q_id_ordered);
        
        
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
	
	// Apply shifts to each of the quantifier variables. 
	// Turns the one 'hasher' tree into a whole set with various shifts applied.
	
	void shifty_looking_geezer(ArrayList<ASTNode> hashers, ArrayList<ASTNode> renamed_q_id_ordered) {
	    for(int idno=0; idno<renamed_q_id_ordered.size(); idno++) {
	        ASTNode id=renamed_q_id_ordered.get(idno);
	        
            int hashers_size=hashers.size();
            for(int i=0; i<hashers_size; i++) {
                ASTNode hasher=hashers.get(i);
                
                // Shift -- add one.
                ReplaceASTNode r=new ReplaceASTNode(id, BinOp.makeBinOp("+",id, new NumberConstant(1)));
                
                ArrayList<ASTNode> temp1=new ArrayList<ASTNode>();
                temp1.add(r.transform(hasher.getChild(0).copy()));
                
                // replace q domain, shift other way.
                ASTNode replacement_q_dom=hasher.getChild(1).getChild(idno).copy().applyShift(-1);
                ASTNode t1=hasher.getChild(1).copy();
                t1.setChild(idno, replacement_q_dom);
                temp1.add(t1);
                
                temp1.add(r.transform(hasher.getChild(2).copy()));
                ASTNode newhasher=new Container(temp1);
                
                newhasher=newhasher.simplify();
                System.out.println("newhasher:" +newhasher);
                hashers.add(newhasher);
                
                
                
                // Shift -- subtract one.
                //ReplaceASTNode r2=new ReplaceASTNode(id, BinOp.makeBinOp("-",id, new NumberConstant(1)));
                
                
                //hashers.add(r2.transform(hasher.copy()));
                
                // That'll do for now.
                
            }
	    }
	}
	
}
