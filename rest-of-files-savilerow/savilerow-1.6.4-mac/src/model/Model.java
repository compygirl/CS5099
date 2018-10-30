package savilerow.model;
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

import savilerow.CmdFlags;
import savilerow.expression.*;
import savilerow.treetransformer.*;
import java.util.*;
import java.io.*;

// Contains a CSP.

public class Model
{
    public ASTNode constraints;
    
    public SymbolTable global_symbols;
    
    public FilteredDomainStore filt;
    
    public ASTNode objective;
    
    public ASTNode branchingon;   // should be a 1-d matrix (or concatenate, or flatten..)
    
    public String heuristic;
    
    public Sat satModel;
    
    public ASTNode incumbentSolution;    // For optimisation using multiple solvers, intermediate solutions stored here. 
    
    public Model(ASTNode c, SymbolTable st, ASTNode ob, ASTNode branch, String h)
    {
        assert c!=null;
        assert st!=null;
        
        constraints=c;
        global_symbols=st;
        objective=ob;
        branchingon=branch;
        heuristic=h;
        
        st.setModel(this);
        
        // Make a default branching on list if there isn't one.
        if(branchingon==null) {
            ArrayList<ASTNode> letgivs=new ArrayList<ASTNode>(global_symbols.lettings_givens);
            ArrayList<ASTNode> bran=new ArrayList<ASTNode>();
            for(int i=0; i<letgivs.size(); i++) {
                if(letgivs.get(i) instanceof Find) {
                    if(letgivs.get(i).getChild(1) instanceof MatrixDomain) {
                        bran.add(new Flatten(letgivs.get(i).getChild(0)));
                    }
                    else {
                        ArrayList<ASTNode> tmp=new ArrayList<ASTNode>();
                        tmp.add(letgivs.get(i).getChild(0));
                        bran.add(new CompoundMatrix(tmp));
                    }
                }
            }
            
            branchingon=new Concatenate(bran);
        }
        
        // Make a filt.
        filt=new FilteredDomainStore(global_symbols);
    }
    
    public Model(ASTNode c, SymbolTable st, FilteredDomainStore f, ASTNode ob, ASTNode branch, String h)
    {
        assert c!=null;
        assert st!=null;
        
        constraints=c;
        global_symbols=st;
        objective=ob;
        branchingon=branch;
        heuristic=h;
        
        st.setModel(this);
        
        // Make a default branching on list if there isn't one.
        if(branchingon==null) {
            ArrayList<ASTNode> letgivs=new ArrayList<ASTNode>(global_symbols.lettings_givens);
            ArrayList<ASTNode> bran=new ArrayList<ASTNode>();
            for(int i=0; i<letgivs.size(); i++) {
                if(letgivs.get(i) instanceof Find) {
                    if(letgivs.get(i).getChild(1) instanceof MatrixDomain) {
                        bran.add(new Flatten(letgivs.get(i).getChild(0)));
                    }
                    else {
                        ArrayList<ASTNode> tmp=new ArrayList<ASTNode>();
                        tmp.add(letgivs.get(i).getChild(0));
                        bran.add(new CompoundMatrix(tmp));
                    }
                }
            }
            
            branchingon=new Concatenate(bran);
        }
        
        filt=f;
    }
    
    // Simplify the model in-place.
    public void simplify()
    {
        //AuditTreeLinks atl=new AuditTreeLinks();
        TransformSimplify ts=new TransformSimplify();
        TransformSimplifyExtended tse=new TransformSimplifyExtended(this);
        
        //atl.transform(constraints);
        global_symbols.simplify();
        if(objective!=null) {
            objective=ts.transform(objective);
            if(objective.getChild(0).isConstant()) {
                CmdFlags.println("Dropping objective: "+objective);
                objective=null;  // Throw away the objective if the expression inside has become a constant. 
            }
        }
        if(branchingon!=null) {
            branchingon=ts.transform(branchingon);
        }
        
        constraints=tse.transform(constraints);   // Does the extended one only on the constraints.
        
        filt.simplify();    //  Allows FilteredDomainStore to get rid of any assigned vars in its stored expressions.
    }
    
    // Substitute an expression throughout.
    // This is used to implement letting. 
    public void substitute(ASTNode toreplace, ASTNode replacement)
    {
        ReplaceASTNode t=new ReplaceASTNode(toreplace, replacement);
        constraints=t.transform(constraints);
        
        if(objective!=null)
            objective=t.transform(objective);
        
        if(branchingon!=null)
            branchingon=t.transform(branchingon);
        
        global_symbols.substitute(toreplace, replacement);
    }
    
    // Substitute an expression everywhere except the second child of a Table
    // constraint within m.constraints. For constant matrices. 
    public void substituteExceptTable(ASTNode toreplace, ASTNode replacement)
    {
        ReplaceASTNode t=new ReplaceASTNode(toreplace, replacement);
        
        if(objective!=null)
            objective=t.transform(objective);
        
        if(branchingon!=null)
            branchingon=t.transform(branchingon);
        
        // HACK -- this transformer was not intended to be used here. 
        //TransformSubInConstantMatrices t4 = new TransformSubInConstantMatrices(this);
        //constraints=t4.transform(constraints);
        
        global_symbols.substitute(toreplace, replacement);
    }
    
    public boolean typecheck() {
        
        // Branching on.
        if(branchingon!=null && branchingon.getDimension()!=1) {
            CmdFlags.println("ERROR: 'branching on' statement may only contain 1-dimensional matrices of decision variables.");
            return false;
        }
        
        //  Objective
        if(objective!=null) {
            if(!objective.typecheck(global_symbols)) return false;
            if(! (objective instanceof Maximising || objective instanceof Minimising) ) {
                CmdFlags.println("ERROR: Objective: "+objective);
                CmdFlags.println("ERROR: should be either minimising or maximising.");
                return false;
            }
            
            if( (!objective.getChild(0).isNumerical()) && (!objective.getChild(0).isRelation()) ) {
                CmdFlags.println("ERROR: Objective must be numerical or relational.");
                return false;
            }
        }
        
        if(!constraints.typecheck(global_symbols)) return false;
        if(!global_symbols.typecheck()) return false;
        return true;
    }
    
    // Givena tree transformer, apply it to this model. 
    public boolean transform(TreeTransformer t) {
        if(CmdFlags.getVerbose()) {
            System.out.println("Rule:"+t.getClass().getName());
        }
        boolean changedModel=false;
        
        assert constraints instanceof Top;
        
        constraints=t.transform(constraints);
        if(t.changedTree) changedModel=true;
        
        if(objective!=null) {
            objective=t.transform(objective);
            if(t.changedTree) changedModel=true;
        }
        
        if(t.getContextCts()!=null) {
            // Extra constraints from the objective. 
            constraints=new Top(new And(constraints.getChild(0), t.getContextCts()));
            changedModel=true;
        }
        
        // WHY not domains??
        
        assert branchingon!=null;
        branchingon=t.transform(branchingon);
        if(t.changedTree) changedModel=true;
        
        if(t.getContextCts()!=null) {
            // Extra constraints from branchingOn
            constraints=new Top(new And(constraints.getChild(0), t.getContextCts()));
            changedModel=true;
        }
        
        if(CmdFlags.getVerbose() && changedModel) {
            System.out.println("Model has changed. Model after rule application:\n"+this.toString());
        }
        
        if(changedModel) {
            simplify();
            
            if(CmdFlags.getVerbose()) {
                System.out.println("Model after rule application and simplify:\n"+this.toString());
            }
        }
        
        assert constraints instanceof Top;
        return changedModel;
    }
    
    // Givena tree transformer, apply it to this model.  
    public void transform_all(TreeTransformer t) {
        if(CmdFlags.getVerbose()) {
            System.out.println("Rule:"+t.getClass().getName());
        }
        
        assert constraints instanceof Top;
        
        constraints=t.transform(constraints);
        
        if(objective!=null) {
            objective=t.transform(objective);
        }
        
        if(branchingon!=null) {
            branchingon=t.transform(branchingon);
        }
        
        // Symbol table. 
        global_symbols.transform_all(t);
        
        if(t.getContextCts()!=null) {
            // Can come from objective or branchingOn
            // Conjoin it to the constraints. 
            constraints=new Top(new And(constraints.getChild(0), t.getContextCts()));
        }
        
        if(CmdFlags.getVerbose()) {
            System.out.println("Model after rule application:\n"+this.toString());
        }
        
        simplify();
        
        if(CmdFlags.getVerbose()) {
            System.out.println("Model after rule application and simplify:\n"+this.toString());
        }
        
        assert constraints instanceof Top;
    }
    
    @Override
    public int hashCode() {
        // Usually if two models are different the constraints must be different. 
        return constraints.hashCode();
    }
    
    @Override
    public boolean equals(Object b)
    {
        if(this.getClass() != b.getClass())
            return false;
        Model c=(Model)b;
        
        if(! c.constraints.equals(constraints))
            return false;
        if(! c.global_symbols.equals(global_symbols))
            return false;
        if( !(  objective==null ? c.objective==null : objective.equals(c.objective)))
            return false;
        if( !( branchingon==null ? c.branchingon==null : branchingon.equals(c.branchingon)))
            return false;
        if( !( heuristic==null ? c.heuristic==null : heuristic.equals(c.heuristic)))
            return false;
        if(! c.filt.equals(filt))
            return false;
        if( !( incumbentSolution==null ? c.incumbentSolution==null : incumbentSolution.equals(c.incumbentSolution)))
            return false;
        
        return true;
    }
    
    public Model copy() {
        // Copy symbol table first.
        SymbolTable newst=global_symbols.copy();
        FilteredDomainStore f=filt.copy(newst);
        //  Identifiers have a reference to the original symbol table. Fix it to point to the copy.
        TransformFixSTRef tf=new TransformFixSTRef(newst);
        
        ASTNode newct=tf.transform(constraints.copy());
        
        ASTNode ob=null;
        if(objective!=null) ob=tf.transform(objective.copy());
        ASTNode bran=null;
        if(branchingon!=null) bran=tf.transform(branchingon.copy());
        
        Model newmodel=new Model(newct, newst, f, ob, bran, heuristic);
        
        if(incumbentSolution!=null) {
            newmodel.incumbentSolution=tf.transform(incumbentSolution.copy());
        }
        
        return newmodel;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //   Output methods. 
    
    public void toMinion(StringBuffer b) {
        toMinion(b, null);
    }
    
    // Output to minion
    public void toMinion(StringBuffer b, ArrayList<ASTNode> scope) {
        b.append("MINION 3\n");
        
        for (String key : CmdFlags.stats.keySet()) {
            b.append("# "+key+" = "+CmdFlags.stats.get(key)+"\n");
        }
        
        b.append("**VARIABLES**\n");    
        global_symbols.toMinion(b);
        
        b.append("**SEARCH**\n");
        if(scope==null) {
            global_symbols.printPrintStmt(b);
        }
        else {
            b.append("PRINT [");
            for(int i=0; i<scope.size(); i++) {
                b.append("[");
                b.append(scope.get(i));
                b.append("]");
                if(i<scope.size()-1) b.append(",");
            }
            b.append("]\n");
        }
        
        if(objective!=null)
            objective.toMinion(b, false);
        
        if(scope!=null) {
            b.append("VARORDER [");
            for(int i=0; i<scope.size(); i++) {
                b.append(scope.get(i));
                if(i<scope.size()-1) b.append(",");
            }
            b.append("]\n");
        }
        else if(branchingon!=null) {
            b.append("VARORDER ");
            if(heuristic!=null) {
                b.append(heuristic);
            }
            else {
                // default var ordering
                b.append("STATIC");
            }
            b.append(" ");
            
            branchingon.toMinion(b, false);
            b.append("\n");
            
            // put ALL variables into a varorder aux, this ensures they'll all 
            // be assigned in any solution produced by minion.
            b.append("VARORDER AUX [");
            global_symbols.printAllVariables(b, ASTNode.Decision);
            StringBuffer c=new StringBuffer();
            global_symbols.printAllVariables(c, ASTNode.Auxiliary);
            if(c.length()>0) {
                b.append(",");
                b.append(c);
            }
            b.append("]\n");
        }
        else {
            // Only have heuristic and not branching on.
            if(heuristic!=null) {
                b.append("VARORDER "+heuristic+" ["); 
            }
            else{
                b.append("VARORDER STATIC [");
            }
            
            global_symbols.printAllVariables(b, ASTNode.Decision);
            b.append("]\n");
            
            b.append("VARORDER AUX [");
            global_symbols.printAllVariables(b, ASTNode.Auxiliary);
            b.append("]\n");
        }
        
        b.append("**CONSTRAINTS**\n");
        constraints.toMinion(b, true);
        
        b.append("**EOF**\n");
    }
    
    public void toDominion(StringBuffer b) {
        b.append("language Dominion 0.0\n");
        
        // Variables, parameters etc
        global_symbols.toDominion(b);
        
        // Optimisation 
        if(objective!=null)
            objective.toDominion(b, false);
        
        b.append("such that\n");
        constraints.toDominion(b, true);
        b.append("\n");
    }
    
    // Output the model in Essence' eventually
    public String toString() {
        StringBuffer s=new StringBuffer();
        s.append("language ESSENCE' 1.0\n");
        s.append(global_symbols.toString());
        if(objective!=null) s.append(objective.toString());
        s.append("such that\n");
        s.append(constraints.toString());
        //s.append(filt.toString());
        return s.toString();
    }
    
    public void toFlatzinc(StringBuffer b) {
        //StringBuffer b=new StringBuffer();
        // get access to some predicates in gecode.
        b.append("predicate all_different_int(array [int] of var int: xs);\n");
        
        global_symbols.toFlatzinc(b);
        constraints.toFlatzinc(b, true);
        
        b.append("solve :: int_search(");
        
        if(branchingon instanceof EmptyMatrix) {
            b.append("[1]");  // Gecode needs something in the list. 
        }
        else {
            branchingon.toFlatzinc(b, false);
        }
        
        b.append(", input_order, indomain_min, complete)\n");
        
        if(objective!=null)
            objective.toFlatzinc(b, false);
        else
            b.append(" satisfy;\n");
        
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //  Minizinc output
    
    public void toMinizinc(StringBuffer b) {
        b.append("% Minizinc model produced by Savile Row from Essence' file "+CmdFlags.eprimefile);
        if(CmdFlags.paramfile!=null) b.append(" and parameter file "+CmdFlags.paramfile);
        b.append("\n");
        
        b.append("include \"globals.mzn\";\n");
        
        global_symbols.toMinizinc(b);
        constraints.toMinizinc(b, true);
        
        b.append("solve :: int_search([");
        
        // Search order annotation. Should look at branchingon.
        global_symbols.printAllVariablesFlatzinc(b, ASTNode.Decision);
        
        b.append("], input_order, indomain_min, complete)\n");
        
        if(objective!=null)
            objective.toMinizinc(b, false);
        else
            b.append(" satisfy;\n");
        
        b.append("output\n [");
        
        global_symbols.showVariablesMinizinc(b);
        
        if(objective!=null) b.append(",show("+objective.getChild(0).toString()+")");
        
        b.append("];\n");
    }
    
    public boolean setupSAT() {
        try {
            satModel=new Sat(this.global_symbols, CmdFlags.satfile);
            satModel.generateVariableEncoding();
            return true;
        }
        catch(IOException e) {
            // Tidy up. 
            File f = new File(CmdFlags.satfile);
            if (f.exists()) f.delete();
            return false;
        }
    }
    
    public boolean toSAT()
    {
        try {
            constraints.toSAT(satModel);
            
            satModel.finaliseOutput();
            return true;
        }
        catch(IOException e) {
            // Tidy up.
            File f = new File(CmdFlags.satfile);
            if (f.exists()) f.delete();
            return false;
        }
    }
}
