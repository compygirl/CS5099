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

// To run before generic flattener. Preferably before any aux vars are introduced.
// Traverse expression tree and flatten identical expressions together, to the same aux variable.  

// Do longer expressions first

// Only match identical expressions (with identical quantification, after renaming).

public class CSEClassIdentical
{
    private HashMap<ASTNode, ArrayList<ASTNode>> exp;
    Model m;
    
    public void flattenCSEs(Model m_in) {
        m=m_in;
        exp=new HashMap<ASTNode, ArrayList<ASTNode>>();
        // Map from a string representation of an expression to a set of pointers to where it occurs in the tree.
        
        populate_exp(m.constraints);
        
        // Dump exp into an arraylist
        ArrayList<Map.Entry<ASTNode, ArrayList<ASTNode>>> exp2=new ArrayList<Map.Entry<ASTNode, ArrayList<ASTNode>>>(exp.entrySet());
        
        // Sort for longest expression first.
        class cmpstrings implements Comparator<Map.Entry<ASTNode, ArrayList<ASTNode>>> {
            public int compare(Map.Entry<ASTNode, ArrayList<ASTNode>> x, Map.Entry<ASTNode, ArrayList<ASTNode>> y) {
                int sizex=x.getValue().get(0).treesize();   // take any one of the AST nodes and get the tree size.
                int sizey=y.getValue().get(0).treesize();
                if(sizex>sizey) return -1;   // x<y
                if(sizex==sizey) return 0;   // x==y
                return 1;  // x>y
            }
        }
        
        Collections.sort(exp2, new cmpstrings());
        ArrayList<ASTNode> new_constraints=new ArrayList<ASTNode>();
        
        iterate_map_entries:
        for(Map.Entry<ASTNode,ArrayList<ASTNode>> entry : exp2) {
            ArrayList<ASTNode> ls=entry.getValue();
            
            // Remove items from ls that are no longer connected to the root because of other CSEs being flattened
            for(int i=0; i<ls.size(); i++) {
                if(ls.get(i).isDetached()) {
                    ls.remove(i);
                    i--;
                }
            }
            
            if(ls.size()>1) {
                CmdFlags.println("CSEClassIdentical: Potential CSE: "+ls);
                
                // Find out if any of them are top-level relations i.e. always true/false
                // COULD get rid of this if there was a rule to take top-level relation aux-vars and assign the var, propagate the consequences 
                // of assigning it through the model.
                boolean toplevel_defined=false;
                boolean toplevel_value=false;  // True or false, i.e. if an expression is found negated at the top level, the toplevel value is false.
                ASTNode toplevel_node=null;
                if(ls.get(0).isRelation()) {
                    for(ASTNode a : ls) {
                        if(a.getParent() instanceof Top || 
                            (a.getParent() instanceof And && a.getParent().getParent() instanceof Top)) {   // Can't use inTopConjunction because it may be in a forall with empty quantifier domain. 
                            toplevel_defined=true;
                            toplevel_value=true;
                            toplevel_node=a;
                        }
                    }
                }
                
                if(toplevel_defined) {
                    // Replace all non-top-level instances of this with toplevel_value
                    for(ASTNode a : ls) {
                        if(a!=toplevel_node) {
                            int childno=a.getParent().getChildren().indexOf(a);
                            ASTNode replacement=new BooleanConstant(toplevel_value);
                            a.getParent().setChild(childno, replacement);
                            replacement.setParent(a.getParent());
                        }
                    }
                    continue iterate_map_entries;
                }
                
                // Standard class-level flattening nicked from TransformToFlatClass
                ASTNode curnode=ls.get(0);
                // Collect all quantifier id's in the exression, and their corresponding quantifier.
                ArrayList<ASTNode> q_id=new ArrayList<ASTNode>();         // Collect quantifier id's in curnode
                ArrayList<ASTNode> quantifiers=new ArrayList<ASTNode>();  // collect quantifiers.
                ArrayList<ASTNode> conditions=new ArrayList<ASTNode>();   // Collect parameter expressions
                
                TransformToFlatClass.findQuantifierIds(q_id, quantifiers, conditions, curnode);
                
                // Extract and process domains from the quantifiers
                ArrayList<ASTNode> qdoms=new ArrayList<ASTNode>();
                TransformToFlatClass.extractDomains(q_id, quantifiers, conditions, qdoms, curnode);
                
                // Save the list in the order they appear in the expression.
                ArrayList<ASTNode> q_id_ordered=new ArrayList<ASTNode>(q_id);
                ArrayList<ASTNode> qdoms_ordered=new ArrayList<ASTNode>(qdoms);
                
                TransformToFlatClass.sortQuantifiers(q_id, quantifiers, qdoms);
                
                
                PairASTNode bnds=curnode.getBoundsAST();
                ASTNode auxvar;
                
                if(q_id.size()>0) {
                    ASTNode auxvarmatrixid=m.global_symbols.newAuxiliaryVariableMatrix(bnds.e1, bnds.e2, q_id, qdoms, conditions);  // This internally special cases for 0/1 vars now.
                    auxvar=new MatrixDeref(auxvarmatrixid, q_id);
                    m.global_symbols.auxVarRepresentsConstraint( auxvarmatrixid.toString(), "CSE: "+ls.size()+" occurrences of: "+curnode.toString());
                }
                else {
                    // It's just a single aux variable.
                    auxvar=m.global_symbols.newAuxiliaryVariable(bnds.e1, bnds.e2);
                    m.global_symbols.auxVarRepresentsConstraint( auxvar.toString(), "CSE: "+ls.size()+" occurrences of: "+curnode.toString());
                }
                
                
                for(int i=0; i<ls.size(); i++) {
                    // Can't replace with same auxvar in each case -- different q_ids.
                    ASTNode auxvarlocal=auxvar.copy();
                    
                    ArrayList<ASTNode> q_id_local=new ArrayList<ASTNode>();         // Collect quantifier id's in curnode
                    ArrayList<ASTNode> quantifiers_local=new ArrayList<ASTNode>();  // collect quantifiers.
                    ArrayList<ASTNode> conditions_local=new ArrayList<ASTNode>();   // Collect parameter expressions
                    TransformToFlatClass.findQuantifierIds(q_id_local, quantifiers_local, conditions_local, ls.get(i));
                    assert q_id_local.size()==q_id_ordered.size();
                    
                    // Swap the q_id the auxvar was created with for the right one in this context. 
                    // Have to put a neutral marker in, so you don't end up with i,j -> j,j -> i,i when swapping i and j
                    for(int j=0; j<q_id_ordered.size(); j++) {
                        ASTNode id=q_id_ordered.get(j);
                        ASTNode rename=new Identifier("ydJDFpqbnGjkzzyu_"+j, m.global_symbols);
                        ReplaceASTNode r=new ReplaceASTNode(id, rename);
                        
                        auxvarlocal=r.transform(auxvarlocal);
                    }
                    for(int j=0; j<q_id_ordered.size(); j++) {
                        ASTNode id=q_id_local.get(j);
                        ASTNode rename=new Identifier("ydJDFpqbnGjkzzyu_"+j, m.global_symbols);
                        ReplaceASTNode r=new ReplaceASTNode(rename, id);
                        
                        auxvarlocal=r.transform(auxvarlocal);
                    }
                    
                    int idx=ls.get(i).getParent().getChildren().indexOf(ls.get(i));
                    CmdFlags.printlnIfVerbose("Replacing: "+ls.get(i).getParent().getChild(idx)+" with: "+auxvarlocal);
                    ls.get(i).getParent().setChild(idx, auxvarlocal);
                }
                
                // Make sure curnode is not copied.
                ASTNode flatcon=new ToVariable(new BooleanConstant(true), auxvar.copy());
                flatcon.setChild_nocopy(0, curnode);
                
                // Wrap flatcon in the conditions.
                if(conditions.size()>0) {
                    flatcon=new Implies(new And(conditions), flatcon); // flatcon must not be copied here.
                }
                
                // Wrap flatcon in the appropriate forall quantifiers.
                for(int i=q_id.size()-1; i>=0; i--) {
                    flatcon=new ForallExpression(q_id.get(i), qdoms.get(i), flatcon);
                }
                //flatcon=flatcon.simplify(); // this might copy curnode
                
                new_constraints.add(flatcon);
            }
        }
        
        // Conjoin all the new constraints onto the top level.
        new_constraints.add(m.constraints.getChild(0));
        m.constraints.setChild(0, new And(new_constraints));
    }
    
    private void populate_exp(ASTNode a)  {
        // If a is a potential CSE...
        // This should be a list of positive things rather than negative! e.g is it numerical or relational.
        if(( a.isRelation()  ||  a.isNumerical() )  // Don't pick up sets or other unusual things
            
            && !a.isConstant()
            && !(a instanceof Identifier)
            && !(a instanceof MatrixDeref)
            && !(a instanceof SafeMatrixDeref)
            && !(a instanceof CompoundMatrix)
            && !(a instanceof EmptyMatrix)
            && !(a instanceof Flatten)
            && !(a instanceof MatrixSlice)
            && !(a instanceof Domain)
            && !(a instanceof ShiftMapper)
            && !(a instanceof MultiplyMapper)
            && a.getCategory()>ASTNode.Quantifier
            ) {
            
            // Collect things.
            // CUT'N'PASTE FROM TransformToFlatClass
            // Collect all quantifier id's in the exression, and their corresponding quantifier.
            ArrayList<ASTNode> q_id=new ArrayList<ASTNode>();         // Collect quantifier id's in curnode
            ArrayList<ASTNode> quantifiers=new ArrayList<ASTNode>();  // collect quantifiers.
            ArrayList<ASTNode> conditions=new ArrayList<ASTNode>();   // Collect parameter expressions
            
            TransformToFlatClass.findQuantifierIds(q_id, quantifiers, conditions, a);
            
            // Extract and process domains from the quantifiers
            ArrayList<ASTNode> qdoms=new ArrayList<ASTNode>();
            TransformToFlatClass.extractDomains(q_id, quantifiers, conditions, qdoms, a);
            
            // Save the list in the order they appear in the expression.
            ArrayList<ASTNode> q_id_ordered=new ArrayList<ASTNode>(q_id);
            ArrayList<ASTNode> qdoms_ordered=new ArrayList<ASTNode>(qdoms);
            
            TransformToFlatClass.sortQuantifiers(q_id, quantifiers, qdoms);
            // End CUT'N'PASTE
            
            // Sub in standard markers for quantifier ID's.
            // cut'n'paste from transformtoflatclass.
            ArrayList<ASTNode> temp=new ArrayList<ASTNode>();
            temp.add(a);
            temp.add(new Container(qdoms_ordered)); temp.add(new Container(conditions));
            ASTNode hasher=new Container(temp);
            
            for(int i=0; i<q_id_ordered.size(); i++) {
                ASTNode id=q_id_ordered.get(i);
                ASTNode rename=new Identifier("ydJDFpqbnGjkzzyu_"+i, m.global_symbols);
                ReplaceASTNode r=new ReplaceASTNode(id, rename);
                
                hasher=r.transform(hasher);
            }
            System.out.println("CSEClassIdentical: Inserting into map: "+hasher.toString());
            
            if(exp.containsKey(hasher)) {
                exp.get(hasher).add(a);
            }
            else {
                ArrayList<ASTNode> list=new ArrayList<ASTNode>();
                list.add(a);
                exp.put(hasher, list);
            }
        }
        
        for(int i=0; i<a.numChildren(); i++) {
            populate_exp(a.getChild(i));
        }
    }
    
    
    
}


