package savilerow.expression;
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

import savilerow.*;
import java.util.*;
import java.math.BigInteger;
import java.io.*;
import savilerow.model.SymbolTable;
import savilerow.model.Sat;

public class WeightedSum extends ASTNode {
    public static final long serialVersionUID = 1L;
    long[] weights;
    
    public WeightedSum(ArrayList<ASTNode> ch) {
        super(ch);
        
        weights= new long[ch.size()];
        for (int i =0; i < ch.size(); i++) {
            weights[i]=1L;
        }
    }
    
    public WeightedSum(ArrayList<ASTNode> ch, ArrayList<Long> w) {
        super(ch);
        weights=new long[w.size()];
        for(int i=0; i<w.size(); i++) weights[i]=w.get(i);
        
        assert ch.size() == w.size();
    }
    
    // More efficient ctor taking arrays.
    // The arrays passed in here are not copied, so must not be altered by the calling
    // method after construction. 
    public WeightedSum(ASTNode[] ch, long[] w) {
        super(ch);
        weights = w;
        assert ch.length == w.length;
    }
    
    // ctor avoids having to make an arraylist if there are only two children.
    public WeightedSum(ASTNode a, ASTNode b) {
        super(a, b);
        weights = new long[2];
        weights[0]=1L;
        weights[1]=1L;
    }
    
    public WeightedSum(ASTNode a, ASTNode b, long aweight, long bweight) {
        super(a, b);
        weights=new long[2];
        weights[0]=aweight;
        weights[1]=bweight;
    }
    
    public ArrayList<Long> getWeights() {
        ArrayList<Long> l = new ArrayList<Long>(weights.length);
        for(int i=0; i<weights.length; i++) l.add(weights[i]);
        return l;
    }
    // Like getChild
    public long getWeight(int i) {
        return weights[i];
    }

    public ASTNode copy() {
        long[] wts=new long[weights.length];
        for(int i=0; i<weights.length; i++) wts[i]=weights[i];
        return new WeightedSum(getChildrenArray(), wts);
    }
    @Override
    public boolean equals(Object other) {
        if (! (other instanceof WeightedSum)) {
            return false;
        }
        WeightedSum oth = (WeightedSum) other;

        if (! oth.getChildren().equals(getChildren())) {
            return false;
        }
        if (! Arrays.equals(oth.weights, weights)) {
            return false;
        }

        return true;
    }
    
    @Override
    public int hashCode() {
        if(hashCache==Integer.MIN_VALUE) {
            int hash = Arrays.hashCode(weights);
            hash = hash * 13 + Arrays.hashCode(children);
            hashCache=hash;  // store
            return hash;
        }
        else {
            return hashCache;
        }
    }

    // Similar to one from And / Or
    public ASTNode simplify() {
        ArrayList<ASTNode> ch = getChildren();
        ArrayList<Long> wts = getWeights();
        boolean changed = false;        // has it changed -- do we return a new WeightedSum?

        assert ch.size() == wts.size();
        // Collect children (and children's children etc) into this.
        for (int i =0; i < ch.size(); i++) {
            if (ch.get(i) instanceof WeightedSum) {
                ASTNode curnode = ch.remove(i);
                long weight = wts.remove(i);
                i--;                // current element removed so move back in list.
                // Add children to end of this list, so that the loop will process them.

                ch.addAll(curnode.getChildren());
                ArrayList<Long> curnode_weights = ((WeightedSum) curnode).getWeights();
                for (int j =0; j < curnode_weights.size(); j++) {
                    wts.add(curnode_weights.get(j) * weight);
                }
                changed = true;
                continue;
            }
            if (ch.get(i) instanceof UnaryMinus) {
                changed = true;
                ch.set(i, ch.get(i).getChild(0));                // get the child of the -
                wts.set(i, -wts.get(i));                // Negate the weight.
            }
        }
        assert ch.size() == wts.size();

        // Constant folding
        int newConstant =0;
        int numConstants =0;
        for (int i =0; i < ch.size(); i++) {
            if (ch.get(i).isConstant()) {
                newConstant += ch.get(i).getValue() * wts.get(i);
                ch.remove(i); wts.remove(i); i--;
                numConstants++;
            }
        }
        if (ch.size() == 0) {
            return new NumberConstant(newConstant);
        }
        assert ch.size() == wts.size();
        if (newConstant != 0) {
            ch.add(new NumberConstant(newConstant));
            wts.add(1L);
            assert ch.size() == wts.size();
        }
        if (numConstants > 1 || (numConstants == 1 && newConstant == 0)) {
            // Merged some constants therefore...
            changed = true;
        }

        // cosort by hashcode before removing duplicates
        // Insertion sort -- behaves well with almost-sorted lists

        ArrayList<Integer> hashcodes = new ArrayList<Integer>(ch.size());
        for (int i =0; i < ch.size(); i++) {
            hashcodes.add(ch.get(i).hashCode());
        }


        for (int i =1; i < ch.size(); i++) {
            for (int j = i - 1; j >= 0; j--) {
                if (hashcodes.get(j + 1) < hashcodes.get(j)) {
                    // swap
                    ASTNode tmp = ch.get(j + 1);
                    ch.set(j + 1, ch.get(j));
                    ch.set(j, tmp);

                    Long tmp2 = wts.get(j + 1);
                    wts.set(j + 1, wts.get(j));
                    wts.set(j, tmp2);

                    // swap hashcodes as well
                    int temp = hashcodes.get(j + 1);
                    hashcodes.set(j + 1, hashcodes.get(j));
                    hashcodes.set(j, temp);
                } else {
                    break;
                }
            }
        }

        // remove dups.
        for (int i =0; i < ch.size() - 1; i++) {
            if (ch.get(i).equals(ch.get(i + 1))) {
                changed = true;
                ch.remove(i + 1);
                wts.set(i, wts.get(i) + wts.remove(i + 1));
                i--;
            }
        }

        // Discard 0-weighted children.
        for (int i =0; i < ch.size(); i++) {
            if (wts.get(i) == 0) {
                changed = true;
                wts.remove(i);
                ch.remove(i);
                i--;
            }
        }

        if (ch.size() == 0) {
            return new NumberConstant(0);
        }
        if (ch.size() == 1 && wts.get(0) == 1) {
            return ch.get(0);
        }
        if (ch.size() == 1 && wts.get(0) == -1) {
            return new UnaryMinus(ch.get(0));
        }
        if (changed) {
            return new WeightedSum(ch, wts);
        }
        return this;
    }
    public boolean typecheck(SymbolTable st) {
        for (int i =0; i < numChildren(); i++) {
            if (!getChild(i).typecheck(st)) {
                return false;
            }
            if (getChild(i).getDimension() > 0) {
                CmdFlags.println("ERROR: Unexpected matrix in weighted sum: " + this);
                return false;
            }
        }
        return true;
    }

    // cosort by hashcode
    // Insertion sort -- behaves well with almost-sorted lists
    public ASTNode normalise() {
        ArrayList<ASTNode> ch = getChildren();
        ArrayList<Long> wts = getWeights();

        ArrayList<Integer> hashcodes = new ArrayList<Integer>(ch.size());
        for (int i =0; i < ch.size(); i++) {
            hashcodes.add(ch.get(i).hashCode());
        }


        boolean changed = false;
        for (int i =1; i < ch.size(); i++) {
            for (int j = i - 1; j >= 0; j--) {
                if (hashcodes.get(j + 1) < hashcodes.get(j)) {
                    // swap
                    ASTNode tmp = ch.get(j + 1);
                    ch.set(j + 1, ch.get(j));
                    ch.set(j, tmp);

                    Long tmp2 = wts.get(j + 1);
                    wts.set(j + 1, wts.get(j));
                    wts.set(j, tmp2);

                    // swap hashcodes as well
                    int temp = hashcodes.get(j + 1);
                    hashcodes.set(j + 1, hashcodes.get(j));
                    hashcodes.set(j, temp);

                    changed = true;
                } else {
                    break;
                }
            }
        }
        if (changed) {
            return new WeightedSum(ch, wts);
        } else {
            return this;
        }
    }

    public boolean isCommutative() {
        return true;
    }

    public boolean isAssociative() {
        return true;
    }

    public String toString() {
        String st = "(";
        for (int i =0; i < numChildren(); i++) {
            if (getChild(i).isConstant()) {
                long val = weights[i] * getChild(i).getValue();
                if (val >= 0 && i > 0) {
                    st += " + ";
                }
                st += val;
            } else {
                if (weights[i] >= 0 && i > 0) {
                    st += " + ";
                }
                if (weights[i] != 1) {
                    st += weights[i] + "*";
                }
                st += getChild(i);
            }
        }
        st += ")";
        return st;
    }

    public boolean toFlatten(boolean propagate) {
        if (getParent() instanceof LessEqual) {
            return false;            // Will be output as sumleq or sumgeq. Only one child of <= can be a sum. 
        }
        return true;
    }
    public boolean isNumerical() {
        return true;
    }
    
    public Intpair getBounds() {
        // Add up all the lower bounds and upper bounds of each term.
        BigInteger lower = BigInteger.valueOf(0L);
        BigInteger upper = BigInteger.valueOf(0L);
        for (int i =0; i < weights.length; i++) {
            Intpair a = getChild(i).getBounds();
            if (weights[i] > 0) {
                lower = lower.add(BigInteger.valueOf(a.lower).multiply(BigInteger.valueOf(weights[i])));
                upper = upper.add(BigInteger.valueOf(a.upper).multiply(BigInteger.valueOf(weights[i])));
            }
            else {
                lower = lower.add(BigInteger.valueOf(a.upper).multiply(BigInteger.valueOf(weights[i])));
                upper = upper.add(BigInteger.valueOf(a.lower).multiply(BigInteger.valueOf(weights[i])));
            }
        }
        
        return lookupBounds(new Intpair(Intpair.BigIntegerToLong(lower), Intpair.BigIntegerToLong(upper)));    //  Look up in FilteredDomainStore
    }
    
    public PairASTNode getBoundsAST() {
        // Add up all the lower bounds and upper bounds of each term.
        ArrayList<ASTNode> lowers = new ArrayList<ASTNode>();
        ArrayList<ASTNode> uppers = new ArrayList<ASTNode>();
        for (int i =0; i < weights.length; i++) {
            PairASTNode a = getChild(i).getBoundsAST();
            if (weights[i] > 0) {
                lowers.add(a.e1);
                uppers.add(a.e2);
            } else {
                lowers.add(a.e2);                // add upper bound to lower list.
                uppers.add(a.e1);
            }
        }

        return new PairASTNode(new WeightedSum(lowers, getWeights()), new WeightedSum(uppers, getWeights()));
    }

    public long getValue() {
        // Should only be called in typecheck, before simplify
        long accumulator =0;
        for (int i =0; i < numChildren(); i++) {
            assert getChild(i).isConstant();
            accumulator += getChild(i).getValue() * weights[i];
        }
        return accumulator;
    }

    public Pair<ASTNode, ASTNode> retrieveConstant() {
        // Split the weighted sum into the constant part and the variable part.
        // Assumes it's simplified so only one constant.
        for (int i =0; i < numChildren(); i++) {
            if (getChild(i) instanceof NumberConstant) {
                ArrayList<ASTNode> ch = getChildren();
                ArrayList<Long> wts = getWeights();

                ASTNode constant = ch.remove(i);
                Long weight = wts.remove(i);
                constant = new NumberConstant(constant.getValue() * weight);

                return new Pair<ASTNode, ASTNode>(constant, new WeightedSum(ch, wts));
            }
        }
        return null;
    }

    //////////////////////////////////////////////////////////////////////////// 
    // 
    // Output methods.
    
    // These may also be called from LessEqual.
    public void toMinionLeq(StringBuffer b, ASTNode aux) {
        boolean all1weights = true;
        for (int i =0; i < weights.length; i++) {
            if (weights[i] != 1) {
                all1weights = false;
                break;
            }
        }

        if (all1weights) {
            b.append("sumleq([");
            for (int i =0; i < numChildren(); i++) { getChild(i).toMinion(b, false); if (i < numChildren() - 1) {
                    b.append(",");
                }
            }
            b.append("],");
            aux.toMinion(b, false);
            b.append(")");
        } else {
            b.append("weightedsumleq([");
            for (int i =0; i < weights.length; i++) { b.append(weights[i]); if (i < weights.length - 1) {
                    b.append(",");
                }
            }
            b.append("],[");
            for (int i =0; i < numChildren(); i++) { getChild(i).toMinion(b, false); if (i < numChildren() - 1) {
                    b.append(",");
                }
            }
            b.append("],");
            aux.toMinion(b, false);
            b.append(")");
        }
    }

    public void toMinionGeq(StringBuffer b, ASTNode aux) {
        boolean all1weights = true;
        for (int i =0; i < weights.length; i++) {
            if (weights[i] != 1) {
                all1weights = false;
                break;
            }
        }

        if (all1weights) {
            b.append("sumgeq([");
            for (int i =0; i < numChildren(); i++) { getChild(i).toMinion(b, false); if (i < numChildren() - 1) {
                    b.append(",");
                } }
            b.append("],");
            aux.toMinion(b, false);
            b.append(")");
        } else {
            b.append("weightedsumgeq([");
            for (int i =0; i < weights.length; i++) { b.append(weights[i]); if (i < weights.length - 1) {
                    b.append(",");
                } }
            b.append("],[");
            for (int i =0; i < numChildren(); i++) { getChild(i).toMinion(b, false); if (i < numChildren() - 1) {
                    b.append(",");
                } }
            b.append("],");
            aux.toMinion(b, false);
            b.append(")");
        }
    }

    //////////////////////////////////////////////////////////////////////////// 
    // Dominion output

    public boolean isAll1Weights() {
        boolean all1weights = true;
        for (int i =0; i < weights.length; i++) {
            if (weights[i] != 1) {
                all1weights = false;
                break;
            }
        }
        return all1weights;
    }

    String mappertype() {
        boolean allpos = true;
        boolean allsign = true;
        for (int i =0; i < weights.length; i++) {
            long w = weights[i];
            if (w != -1 && w != 1) {
                allsign = false;
            }
            if (w < 0) {
                allpos = false;
            }
        }
        if (allsign) {
            return "signmult";
        }
        if (allpos) {
            return "posmult";
        }
        return "mult";
    }

    public void toDominionLeq(StringBuffer b, ASTNode aux) {
        toDominionOp(b, aux, "sumleq");
    }
    public void toDominionGeq(StringBuffer b, ASTNode aux) {
        toDominionOp(b, aux, "sumgeq");
    }

    public void toDominionOp(StringBuffer b, ASTNode aux, String ctname) {
        if (isAll1Weights()) {
            b.append(CmdFlags.getCtName() + " ");
            b.append(ctname + "([");
            for (int i =0; i < numChildren(); i++) { getChild(i).toDominion(b, false); if (i < numChildren() - 1) {
                    b.append(",");
                } }
            b.append("],");
            aux.toDominion(b, false);
            b.append(")");
        } else {
            String maptype = mappertype();
            b.append(CmdFlags.getCtName() + " ");
            b.append(ctname + "([");
            for (int i =0; i < weights.length; i++) {
                b.append(maptype);
                b.append("(");
                getChild(i).toDominion(b, false);
                b.append("," + weights[i] + ")");
                if (i < weights.length - 1) {
                    b.append(",");
                }
            }
            b.append("],");
            aux.toDominion(b, false);
            b.append(")");
        }
    }

    public void toDominionParam(StringBuffer b) {
        if (numChildren() > 1) {
            b.append("Sum([");
        }
        for (int i =0; i < numChildren(); i++) {
            if (! (weights[i]==1L)) {
                b.append("Product(" + weights[i] + ",");
            }
            getChild(i).toDominionParam(b);
            if (! (weights[i]==1L)) {
                b.append(")");
            }
            if (i < numChildren() - 1) {
                b.append(",");
            }
        }
        if (numChildren() > 1) {
            b.append("])");
        }
    }
    public void toDominionWithAuxVar(StringBuffer b, ASTNode aux) {
        if (isAll1Weights()) {
            b.append(CmdFlags.getCtName() + " ");
            b.append("sum([");
            for (int i =0; i < numChildren(); i++) { getChild(i).toDominion(b, false); if (i < numChildren() - 1) {
                    b.append(",");
                } }
            b.append("],");
            aux.toDominion(b, false);
            b.append(")");
        } else {
            String maptype = mappertype();
            b.append(CmdFlags.getCtName() + " ");
            b.append("sum([");
            for (int i =0; i < weights.length; i++) {
                b.append(maptype);
                b.append("(");
                getChild(i).toDominion(b, false);
                b.append("," + weights[i] + ")");
                if (i < weights.length - 1) {
                    b.append(",");
                }
            }
            b.append("],");
            aux.toDominion(b, false);
            b.append(")");
        }
    }

    // Flatzinc ones.
    public void toFlatzincLeq(StringBuffer b, ASTNode aux) {
        b.append("constraint int_lin_le([");
        for (int i =0; i < weights.length; i++) { b.append(weights[i]); b.append(","); }
        b.append("-1],[");
        for (int i =0; i < numChildren(); i++) { getChild(i).toFlatzinc(b, false); b.append(","); }
        aux.toFlatzinc(b, false);        // put aux var into sum, with weight -1
        b.append("],0);");        // le 0.
    }


    public void toFlatzincGeq(StringBuffer b, ASTNode aux) {
        b.append("constraint int_lin_le([");
        for (int i =0; i < weights.length; i++) { b.append(-weights[i]); b.append(","); }
        b.append("1],[");
        for (int i =0; i < numChildren(); i++) { getChild(i).toFlatzinc(b, false); b.append(","); }
        aux.toFlatzinc(b, false);        // put into sum with weight 1.
        b.append("],0);");
    }

    public void toFlatzincWithAuxVar(StringBuffer b, ASTNode aux) {
        if(! aux.isConstant()) {
            // Rearrange to put 0 on right hand side. 
            b.append("constraint int_lin_eq([");
            for (int i =0; i < weights.length; i++) { b.append(weights[i]); b.append(","); }
            b.append("-1],[");
            for (int i =0; i < numChildren(); i++) { getChild(i).toFlatzinc(b, false); b.append(","); }
            aux.toFlatzinc(b, false);
            b.append("],0);");
        }
        else {
            // Already constant on right hand side. 
            b.append("constraint int_lin_eq([");
            for (int i =0; i < weights.length; i++) { 
                b.append(weights[i]);
                if(i<weights.length-1) b.append(",");
            }
            b.append("],[");
            for (int i =0; i < numChildren(); i++) { 
                getChild(i).toFlatzinc(b, false);
                if(i<weights.length-1) b.append(","); 
            }
            b.append("],");
            aux.toFlatzinc(b, false);
            b.append(");");
        }
    }

    public void toMinizinc(StringBuffer b, boolean bool_context) {
        b.append("(");
        for (int i =0; i < numChildren(); i++) {
            b.append("(");
            if (weights[i] != 1) {
                b.append(weights[i]);
                b.append("*");
            }
            getChild(i).toMinizinc(b, false);
            b.append(")");
            if (i < numChildren() - 1) {
                b.append("+");
            }
        }
        b.append(")");
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //  SAT encoding
    
    //  Order encoding exactly following Jeavons and Petke.
    //  w is a local copy of weights. 
    public void orderEncodingLeq(Sat satModel, long[] w, long leqValue, long aux) throws IOException {
        long[] tuple=new long[numChildren()];
        
        // Find range of each term. 
        Intpair[] ranges=new Intpair[numChildren()];
        for(int i=0; i<numChildren(); i++) {
            ranges[i]=getChild(i).getBounds();
            if(w[i]>0) {
                ranges[i].lower=w[i]*ranges[i].lower;
                ranges[i].upper=w[i]*ranges[i].upper;
            }
            else {
                // swap. 
                long tmp=w[i]*ranges[i].lower;
                ranges[i].lower=w[i]*ranges[i].upper;
                ranges[i].upper=tmp;
            }
        }
        
        orderEncodingLeqHelper(satModel, w, leqValue, ranges, tuple, 0, aux);
    }
    
    // Pos ranges from 0 to numChildren()-1 when filling in tuple. 
    public void orderEncodingLeqHelper(Sat satModel, long[] w, long leqValue, Intpair[] ranges, long[] tuple, int pos, long aux) throws IOException {
        // Base case first. 
        if(pos==numChildren()) {
            ArrayList<Long> clause=new ArrayList<Long>();
            if(aux!=0) clause.add(aux);
            boolean clauseTrue=false;   // Does the clause contain the true literal. 
            for(int i=0; i<numChildren(); i++) {
                long addlit;
                if(w[i]>0) {
                    addlit=getChild(i).orderEncode(satModel, (long) Math.floor(((double)tuple[i])/w[i]));
                }
                else {
                    addlit=-getChild(i).orderEncode(satModel, (long) Math.ceil(((double)tuple[i])/w[i])-1);
                }
                // If new literal is true, 
                if(addlit==satModel.getTrue()) {
                    clauseTrue=true;
                    break;
                }
                
                // If new literal is not false, add to clause. 
                if(addlit != -satModel.getTrue()) {
                    clause.add(addlit);
                }
            }
            if(!clauseTrue) satModel.addClause(clause);
        }
        else if(pos==numChildren()-1) {
            // Special case for last child. Calculate the value instead of iterating through all values.
            long sum=0;
            for(int i=0; i<numChildren()-1; i++) sum+=tuple[i];
            
            long finalValue=leqValue-numChildren()+1-sum;
            
            if(finalValue<=ranges[pos].upper && finalValue>=ranges[pos].lower-1) {
                tuple[pos]=finalValue;
                orderEncodingLeqHelper(satModel, w, leqValue, ranges, tuple, pos+1, aux);
            }
        }
        else {
            for(long val=ranges[pos].lower-1; val<=ranges[pos].upper; val++) {
                tuple[pos]=val;
                orderEncodingLeqHelper(satModel, w, leqValue, ranges, tuple, pos+1, aux);
            }
        }
    }
    
    public void toSATLeq(Sat satModel, long leqValue) throws IOException {
        //  Assumes we have  sum <= constant.
        long[] w=new long[numChildren()];
        for(int i=0; i<numChildren(); i++) w[i]=weights[i];
        
        orderEncodingLeq(satModel, w, leqValue, 0);
    }
    
    public void toSATGeq(Sat satModel, long geqValue) throws IOException {
        // We have  sum >= constant. Negate all weights and the constant to make -sum <= -constant.
        long[] w=new long[numChildren()];
        for(int i=0; i<numChildren(); i++) w[i]=-weights[i];
        
        orderEncodingLeq(satModel, w, -geqValue, 0);
    }
    
    public void toSATLeqWithAuxVar(Sat satModel, long leqValue, long aux) throws IOException {
        // First, generate aux ->   this<=leqvalue.
        long[] w=new long[numChildren()];
        for(int i=0; i<numChildren(); i++) w[i]=weights[i];
        
        orderEncodingLeq(satModel, w, leqValue, -aux);   //  Adds -aux to all clauses.
        
        // Second, generate -aux ->  this>=leqvalue+1
        for(int i=0; i<numChildren(); i++) w[i]=-weights[i];
        
        orderEncodingLeq(satModel, w, -(leqValue+1), aux);   //  Adds aux to all clauses.
    }

    public void toSATGeqWithAuxVar(Sat satModel, long geqValue, long aux) throws IOException {
        // First, generate aux ->   this>=geqvalue.
        long[] w=new long[numChildren()];
        for(int i=0; i<numChildren(); i++) w[i]=-weights[i];
        
        orderEncodingLeq(satModel, w, -geqValue, -aux);   //  Adds -aux to all clauses.
        
        // Second, generate -aux ->  this<=geqvalue-1
        for(int i=0; i<numChildren(); i++) w[i]=weights[i];
        
        orderEncodingLeq(satModel, w, geqValue-1, aux);   //  Adds aux to all clauses.
    }
    
	////////////////////////////////////////////////////////////////////////////
	// SAT encoding -- Binary sum in ToVariable.
	
    public void toSATWithAuxVar(Sat satModel, ASTNode auxVar) throws IOException {
        assert CmdFlags.getSatAlt();
        satModel.ternaryEncoding(this, auxVar);
    }
    
    public boolean test(long val1, long val2, long aux) {
        return func(val1, val2)==aux;
    }
    public long func(long val1, long val2) {
        return weights[0]*val1 + weights[1]*val2;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //  Symmetry
    
    public void toJSON(StringBuffer bf) {
        if (getParent() != null && getParent().canChildBeConvertedToDifference(getChildNo()) && isValidDifference()) {
            toJSONAsDifference(bf);
            return;
        }
        toJSONHeader(bf, true);
        bf.append("\"children\": [");
        for (int i = 0; i < numChildren(); i++) {
            bf.append("\n");
            // make times object out of weight and variable/constant
            // first convert weight to constant node type
            NumberConstant nc = new NumberConstant(weights[i]);
            Times tempTimes = new Times(nc, getChild(i));
            tempTimes.toJSON(bf);
            bf.append(",");
        }
        bf.append("]\n}");
    }
    
    public boolean childrenAreSymmetric() {
        return true;
    }

    /**
     * Checks if this sum consists of weights of the same magnitude   
     * @return true if the above cases are matched
     */
    public boolean isValidDifference() {
        long firstWeight = Math.abs(weights[0]);
        for (int i = 1; i < numChildren(); i++) {
            if (firstWeight != Math.abs(weights[i]))
                return false;
        }
        return true;
    }
    
    public void toJSONAsDifference(StringBuffer bf) {
        String json = "{\n\"type\": \"Difference\",\n\"symmetricChildren\": true,\n\"children\": [";
        ArrayList<ASTNode> tempNodes = collectNodesOfSign(true); //collect positive 
        ArrayList<Long> tempWeights = makeFilledList(tempNodes.size(), Math.abs(weights[0]));
        WeightedSum leftSum = new WeightedSum(tempNodes, tempWeights);
        tempNodes = collectNodesOfSign(false); //collect  nodesnegative
        tempWeights = makeFilledList(tempNodes.size(), Math.abs(weights[0]));
        WeightedSum rightSum = new WeightedSum(tempNodes, tempWeights);
        
        bf.append(json);
        
        bf.append("\n");
        leftSum.toJSON(bf);
        bf.append(",\n");
        rightSum.toJSON(bf);
        bf.append("]\n}");
    }
    
    public ArrayList<ASTNode> collectNodesOfSign(boolean positive) {
        ArrayList<ASTNode> nodes = new ArrayList<ASTNode>();
        for (int i = 0; i < numChildren(); i++) {
            if ((positive && weights[i] > 0) || (!positive && weights[i] < 0))
                nodes.add(getChild(i));
        }
        return nodes;
    }

    public ArrayList<Long> makeFilledList(int size, long value) {
        ArrayList<Long> longs = new ArrayList<Long>();
        for (int i = 0; i < size; i++) {
            longs.add(value);
        }
        return longs;
    }
}
