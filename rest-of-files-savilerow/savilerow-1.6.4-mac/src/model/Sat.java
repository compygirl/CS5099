package savilerow.model;
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

import savilerow.expression.ASTNode;

import java.util.ArrayList;
import java.util.HashMap;
import savilerow.expression.*;
import savilerow.model.categoryentry;
import java.util.*;
import java.io.*;
import savilerow.CmdFlags;

import gnu.trove.map.hash.*;

public class Sat
{
    private long variableNumber=1;
    
    private long numClauses=0;
    
    //Maps the SAT variables (direct only) to SR variable/value.
    //private TLongIntHashMap dimacsMappingVar;
    //private TLongLongHashMap dimacsMappingVal;
    private TLongObjectHashMap<NumberMap> dimacsMapping;
    
    // Just enough info to decode order encoding, for SR vars that have only the order encoding. 
    public TLongObjectHashMap<NumberMap> orderMappingMin;
    public TLongObjectHashMap<NumberMap> orderMappingMid;
    public TLongObjectHashMap<NumberMap> orderMappingMax;
    
    //Maps Variable objects to their respective values in CNF form
    private TObjectLongHashMap<NumberMap> directVarMapping;
    
    private TObjectLongHashMap<NumberMap> orderVarMapping;
    
    private SymbolTable global_symbols;
    
    private String satFileName;
    
    private BufferedWriter outstream;
    
    private long trueVar;   //  A SAT variable that is true in all solutions.
    // trueVar or -trueVar is returned when a SAT variable is requested that 
    // does not exist. 
    
    public Sat(SymbolTable _global_symbols, String _satFileName)
    {
        dimacsMapping=new TLongObjectHashMap<NumberMap>();
        //dimacsMappingVar=new TLongIntHashMap();
        //dimacsMappingVal=new TLongLongHashMap();
        
        directVarMapping=new TObjectLongHashMap<NumberMap>();
        orderVarMapping=new TObjectLongHashMap<NumberMap>();
        
        orderMappingMin=new TLongObjectHashMap<NumberMap>();
        orderMappingMid=new TLongObjectHashMap<NumberMap>();
        orderMappingMax=new TLongObjectHashMap<NumberMap>();
        global_symbols=_global_symbols;
        satFileName=_satFileName;
        
        assert directVarMapping.getNoEntryValue()==0L;
        assert orderVarMapping.getNoEntryValue()==0L;
        
        try {
            outstream=new BufferedWriter(new FileWriter(satFileName));
            
            // Write 100 spaces at start of file, to leave space for "p cnf" line.
            for(int i=0; i<10; i++) {
                outstream.write("          ");
            }
            outstream.newLine();
            // Make the true variable. 
            trueVar=getNextVariableNumber();
            // Do not add to mappings.
            // Make trueVar true.
            addClause( String.valueOf(trueVar) );
        }
        catch ( IOException e ) {
            CmdFlags.errorExit("Failed to open or write to SAT output file.");
        }
    }
    
    //Returns the next available SAT variable number.
    private long getNextVariableNumber() {
        return variableNumber++;
    }
    
    public NumberMap getDimacsMapping(long assign) {
        return dimacsMapping.get(assign);
        //int var_number=dimacsMappingVar.get(assign);
        //if(var_number==dimacsMappingVar.getNoEntryValue()) return null;
        //return new NumberMap(dimacsMappingVal.get(assign), varNames.get(var_number));
    }
    
    // Create variable for the fact  variableName = value
    private long createSatVariableDirect(String variableName, long value) {
        NumberMap map=new NumberMap(value, variableName);
        long satvar=getNextVariableNumber();
        directVarMapping.put(map, satvar);
        
        dimacsMapping.put(satvar, map);
        //dimacsMappingVar.put(satvar, varNums.get(variableName));
        //dimacsMappingVal.put(satvar, value);
        return satvar;
    }
    
    // Create variable for the fact  variableName <= value
    private long createSatVariableOrder(String variableName, long value) {
        NumberMap map=new NumberMap(value, variableName);
        long satvar=getNextVariableNumber();
        orderVarMapping.put(map, satvar);
        return satvar;
    }
    
    public long getOrderVariable(String variableName, long value) {
        long l=orderVarMapping.get(new NumberMap(value, variableName));
        if(l==orderVarMapping.getNoEntryValue()) {
            Intpair bnds=global_symbols.getDomain(variableName).getBounds();
            if(value<bnds.lower) {
                return -trueVar;  // false.
            }
            if(value>=bnds.upper) {
                return trueVar;   // true.
            }
            
            ArrayList<Intpair> intervals=global_symbols.getDomain(variableName).getIntervalSet();
            for(int i=0; i<intervals.size()-1; i++) {
                Intpair interval1=intervals.get(i);
                Intpair interval2=intervals.get(i+1);
                // Test if the value is between the two intervals. 
                
                if(value>interval1.upper && value<interval2.lower) {
                    long l2=orderVarMapping.get(new NumberMap(interval1.upper, variableName));
                    assert l2!=orderVarMapping.getNoEntryValue();
                    return l2;
                }
            }
            assert false;
            return 1;
        }
        else {
            return l;
        }
    }
    
    public long getDirectVariable(String variableName, long value) {
        long l=directVarMapping.get(new NumberMap(value, variableName));
        if(l==directVarMapping.getNoEntryValue()) {
            return -trueVar;  // false.
        }
        else {
            return l;
        }
    }
    
    public long getTrue() {
        return trueVar;
    }
    
    // Create a new SAT variable used as an auxiliary when encoding a 
    // constraint. 
    // This is represented in the directVarMapping table using the number of the
    // variable as its name, so literals are available for var=1 and var=0
    
    // Should not have any identifiers with the number of the sat variable as their name. Removing from direct mapping table. 
    public long createAuxSATVariable() {
        return getNextVariableNumber();
    }
    
    public ASTNode createAuxSATVariableAST() {
        long satvar=createAuxSATVariable();
        return new SATLiteral(satvar, this);
    }
    
    //  Generate both the direct and order encoding variables, and the channelling clauses
    //  for each of the Savile Row variables.
    public void generateVariableEncoding() throws IOException
    {
        categoryentry catentry=global_symbols.getCategoryFirst();
        while (catentry!=null)
        {
            if(catentry.cat == ASTNode.Decision || catentry.cat == ASTNode.Auxiliary)
            {
                String ct = global_symbols.represents_ct.get(catentry.name);
                if (ct == null) {
                    ct = "";
                }
                addComment("Encoding variable: "+catentry.name+" with domain: "+global_symbols.getDomain(catentry.name)+" (representing constraint "+ct+")");
                
                ASTNode domain=global_symbols.getDomain(catentry.name);
                
                ArrayList<Intpair> intervalset=domain.getIntervalSet();
                
                // Are there only two values?
                long numvals=0;
                for(int i=0; i<intervalset.size(); i++) {
                    numvals=numvals+intervalset.get(i).upper-intervalset.get(i).lower+1;
                }
                
                if(numvals<=0) {
                    //  Ensure no solutions.
                    addClause(-getTrue());
                }
                else if(numvals==1) {
                    generateVariableEncoding1Val(catentry.name);
                }
                else if(numvals==2) {
                    generateVariableEncoding2Vals(catentry.name);
                }
                else {
                    if(global_symbols.isDirectSAT(catentry.name)) {
                        //  Need direct encoding as well as order.
                        generateVariableEncodingInteger(catentry.name);
                    }
                    else {
                        generateVariableEncodingIntegerNoDirect(catentry.name);
                    }
                }
            }
            catentry=catentry.next;
        }
    }
    
    public void generateVariableEncoding1Val(String name) throws IOException
    {
        ASTNode domain=global_symbols.getDomain(name);
        Intpair bnds=domain.getBounds();
        assert bnds.upper==bnds.lower;
        
        long val1=bnds.lower;
        
        long satvar=getNextVariableNumber();
        addComment("Var represented with SAT variable "+satvar);
        
        NumberMap val1map=new NumberMap(val1, name);
        
        directVarMapping.put(val1map, getTrue());
        // Make sure the variable is always true
        addClause(satvar);
        
        dimacsMapping.put(satvar, val1map);
    }
    
    public void generateVariableEncoding2Vals(String name) throws IOException
    {
        // Encode an SR variable with 2 values as a single SAT variable. 
        
        ASTNode domain=global_symbols.getDomain(name);
        Intpair bnds=domain.getBounds();
        
        long val1=bnds.lower;
        long val2=bnds.upper;
        
        // Make the SAT variable without using createSatVariableDirect
        long satvar=getNextVariableNumber();
        addComment("Var represented with SAT variable "+satvar);
        
        NumberMap val1map=new NumberMap(val1, name);
        NumberMap val2map=new NumberMap(val2, name);
        
        directVarMapping.put(val2map, satvar);
        directVarMapping.put(val1map, -satvar);
        
        //  A two-valued variable is always <= val2
        orderVarMapping.put(val2map, trueVar);
        // The variable is <=val1 if it is not =val2. 
        orderVarMapping.put(val1map, -satvar);
        
        // For translation back from SAT solution.
        dimacsMapping.put(satvar, val2map);
        dimacsMapping.put(-satvar, val1map);
    }
    
    public void generateVariableEncodingInteger(String name) throws IOException 
    {
        ASTNode domain=global_symbols.getDomain(name);
        ArrayList<Intpair> intervalset=domain.getIntervalSet();
        
        // Need to keep track of the previous order variable. Initially false. (x <= lowerbound-1 is false) 
        long prevordervar=-trueVar;
        
        for(int intervalidx=0; intervalidx<intervalset.size(); intervalidx++) {
            Intpair bnds=intervalset.get(intervalidx);
            
            for (long i=bnds.lower; i<=bnds.upper; i++)
            {
                boolean lastValue= i==bnds.upper && intervalidx==intervalset.size()-1;
                
                long directvar;
                
                if(lastValue) {
                    directvar=-prevordervar; // last direct variable is the negation of second-last order variable.
                    
                    // Because no SAT variable is created, need to add some mappings. 
                    NumberMap directmap=new NumberMap(i, name);
                    directVarMapping.put(directmap, directvar);
                    dimacsMapping.put(directvar, directmap);
                    //dimacsMappingVar.put(directvar, varNums.get(name));
                    //dimacsMappingVal.put(directvar, i);
                }
                else {
                    directvar=createSatVariableDirect(name, i);
                }
                
                long ordervar=trueVar;
                
                if(!lastValue) {
                    ordervar=createSatVariableOrder(name, i);
                    
                    // Ladder clause   e.g. [x<=5] -> [x<=6]
                    if( prevordervar != -trueVar ) {
                        addClause( -prevordervar + " " + ordervar);
                    }
                }
                
                if(!lastValue) {
                    // Channelling clauses -- connect the direct and order variable.
                    
                    // e.g. -[x<=5] /\ [x<=4] -> [x=4]
                    if( prevordervar != -trueVar) {    // PROBABLY NO LONGER NEED TO CHECK THIS!!
                        addClause( prevordervar+" "+ (-ordervar)+" "+directvar);
                    }
                    else {
                        addClause( (-ordervar)+" "+directvar);
                    }
                    
                    // e.g. [x=5] -> [x<=5]
                    addClause( (-directvar)+" "+ordervar );
                    
                    // e.g. [x=5] -> -[x<=4]
                    if( prevordervar != -trueVar) {    // PROBABLY NO LONGER NEED TO CHECK THIS!!
                        addClause( (-directvar)+" "+(-prevordervar) );
                    }
                }
                
                prevordervar=ordervar;  // For next iteration.
            }
        }
        
        // Optional extra: AMO clauses.
        /*for(int intervalidx=0; intervalidx<intervalset.size(); intervalidx++) {
            Intpair bnds=intervalset.get(intervalidx);
            
            for(long i=bnds.lower; i<=bnds.upper; i++)
            {
                for(int intervalidx2=intervalidx; intervalidx2<intervalset.size(); intervalidx2++) {
                    Intpair bnds2=intervalset.get(intervalidx2);
                    
                    for(long j=Math.max(bnds2.lower, i+1); j<=bnds2.upper; j++) {
                        addClause((-getDirectVariable(name, i))+" "+(-getDirectVariable(name, j)));
                    }
                }
            }
        }*/
        
        // Optional extra: ALO clause
        ArrayList<Long> alo=new ArrayList<Long>();
        for(int intervalidx=0; intervalidx<intervalset.size(); intervalidx++) {
            Intpair bnds=intervalset.get(intervalidx);
            
            for(long i=bnds.lower; i<=bnds.upper; i++)
            {
                alo.add(getDirectVariable(name, i));
            }
        }
        addClause(alo);
        
    }
    
    public void generateVariableEncodingIntegerNoDirect(String name) throws IOException 
    {
        ASTNode domain=global_symbols.getDomain(name);
        ArrayList<Intpair> intervalset=domain.getIntervalSet();
        
        // Need to keep track of the previous order variable. Initially false. (x <= lowerbound-1 is false) 
        long prevordervar=-trueVar;
        
        for(int intervalidx=0; intervalidx<intervalset.size(); intervalidx++) {
            Intpair bnds=intervalset.get(intervalidx);
            
            for (long i=bnds.lower; i<=bnds.upper; i++)
            {
                boolean lastValue= i==bnds.upper && intervalidx==intervalset.size()-1;
                
                NumberMap n=new NumberMap(i, name);
                
                if(!lastValue) {
                    long ordervar=createSatVariableOrder(name, i);
                    
                    // Ladder clause   e.g. [x<=5] -> [x<=6]
                    if( prevordervar != -trueVar ) {
                        addClause( -prevordervar + " " + ordervar);
                        
                        //  Populate tables for mapping back to SR var/val.
                        orderMappingMid.put(ordervar, n);
                    }
                    else {
                        orderMappingMin.put(ordervar, n);
                    }
                    
                    prevordervar=ordervar;  // For next iteration.
                }
                else {
                    orderMappingMax.put(-prevordervar, n);  //  If topmost order var is false, that indicates the max value of the domain is assigned. 
                }
            }
        }
    }
    
    public void addClause(String clause) throws IOException
    {
        outstream.write(clause);
        outstream.write(" 0");
        outstream.newLine();
        numClauses++;
        
        if(CmdFlags.getCNFLimit()!=0) {
            if(numClauses>CmdFlags.getCNFLimit()) {
                CmdFlags.println("ERROR: Reached CNF clause limit.");
                throw new IOException();
            }
        }
    }
    
    public void addClause(long lit1) throws IOException
    {
        outstream.write(String.valueOf(lit1));
        outstream.write(" 0");
        outstream.newLine();
        numClauses++;
        
        if(CmdFlags.getCNFLimit()!=0) {
            if(numClauses>CmdFlags.getCNFLimit()) {
                CmdFlags.println("ERROR: Reached CNF clause limit.");
                throw new IOException();
            }
        }
    }
    
    public void addClause(ArrayList<Long> literals) throws IOException
    {
        for(int i=0; i<literals.size(); i++) {
            outstream.write(String.valueOf(literals.get(i)));
            outstream.write(" ");
        }
        outstream.write("0");
        outstream.newLine();
        
        numClauses++;
        
        if(CmdFlags.getCNFLimit()!=0) {
            if(numClauses>CmdFlags.getCNFLimit()) {
                CmdFlags.println("ERROR: Reached CNF clause limit.");
                throw new IOException();
            }
        }
    }
    
    public void addClauseReified(ArrayList<Long> literals, long auxVar) throws IOException
    {
        // auxVar -> literals
        outstream.write(String.valueOf(-auxVar));
        outstream.write(" ");
        
        for(int i=0; i<literals.size(); i++) {
            outstream.write(String.valueOf(literals.get(i)));
            outstream.write(" ");
        }
        outstream.write("0");
        outstream.newLine();
        numClauses++;
        
        // For each literal, literal -> auxVar.
        for(int i=0; i<literals.size(); i++) {
            outstream.write(String.valueOf(-literals.get(i)));
            outstream.write(" ");
            outstream.write(String.valueOf(auxVar));
            outstream.write(" 0");
            outstream.newLine();
            numClauses++;
        }
        
        if(CmdFlags.getCNFLimit()!=0) {
            if(numClauses>CmdFlags.getCNFLimit()) {
                CmdFlags.println("ERROR: Reached CNF clause limit.");
                throw new IOException();
            }
        }
    }
    
    public void addComment(String comment) throws IOException
    {
        outstream.write("c ");
        outstream.write(comment);
        outstream.newLine();
    }
    
    public void finaliseOutput() throws IOException
    {
        outstream.close();
        RandomAccessFile f=new RandomAccessFile(satFileName, "rw");
        f.seek(0);
        byte[] pcnf=("p cnf "+(variableNumber-1)+" "+numClauses).getBytes();
        f.write(pcnf);
        f.close();
    }
    
    public void addClauseAfterFinalise(String clause) throws IOException
    {
        outstream=new BufferedWriter(new FileWriter(satFileName, true));  // append
        addClause(clause);
        finaliseOutput();
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //     Methods for SAT encodings, direct & support
    
	// Helper for SAT encoding.
	public static ArrayList<Intpair> getIntervalSetSAT(ASTNode a) {
        if (a.isConstant()) {
            ArrayList<Intpair> i = new ArrayList<Intpair>();
            i.add(new Intpair(a.getValue(), a.getValue()));
            return i;
        }
        else if (a instanceof Identifier) {
            Identifier identifier=(Identifier) a;
            return identifier.global_symbols.getDomain(identifier.getName()).getIntervalSet();
        }
        else if(a instanceof SATLiteral) {
            ArrayList<Intpair> i = new ArrayList<Intpair>();
            i.add(new Intpair(0, 1));
            return i;
        }
        return null;
    }
    
    //   Direct encoding of unary constraints
    public void unaryDirectEncoding(ASTNode constraint, ASTNode var) throws IOException
    {
        ArrayList<Intpair> domain1=getIntervalSetSAT(var);
        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                if(!constraint.test(i))
                {
                    addClause(String.valueOf(-var.directEncode(this, i)));
                }
            }
        }
    }
    
    //   Direct encoding of unary constraints with aux.
    public void unaryDirectEncodingWithAuxVar(ASTNode constraint, ASTNode var, long aux) throws IOException
    {
        ArrayList<Intpair> domain1=getIntervalSetSAT(var);
        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                if(!constraint.test(i))
                {
                    addClause((-aux)+" "+(-var.directEncode(this, i)));
                }
                else {
                    addClause(aux+" "+(-var.directEncode(this, i)));
                }
            }
        }
    }
    
    public void directEncoding(ASTNode constraintNode, ASTNode node1, ASTNode node2) throws IOException
    {
        ArrayList<Intpair> domain1=getIntervalSetSAT(node1);
        ArrayList<Intpair> domain2=getIntervalSetSAT(node2);


        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                for (Intpair pair2: domain2)
                {
                    for (long j=pair2.lower; j<=pair2.upper; j++)
                    {

                        if (!constraintNode.test(i,j))
                        {
                            String clause=(-node1.directEncode(this, i)) + " " + (-node2.directEncode(this, j)); 
                            addClause(clause);
                        }

                    }
                }
            }

        }

    }              

    public void directEncodingWithAuxVar(ASTNode constraintNode, ASTNode node1, ASTNode node2, long auxVarValue) throws IOException
    {
        ArrayList<Intpair> domain1=getIntervalSetSAT(node1);
        ArrayList<Intpair> domain2=getIntervalSetSAT(node2);


        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                for (Intpair pair2: domain2)
                {
                    for (long j=pair2.lower; j<=pair2.upper; j++)
                    {

                        if (!constraintNode.test(i,j))
                        {
                            String clause=(-auxVarValue) + " " + (-node1.directEncode(this, i)) + " " + (-node2.directEncode(this, j));
                            addClause(clause);
                        }
                        else {
                            String clause=auxVarValue + " " + (-node1.directEncode(this, i)) + " " + (-node2.directEncode(this, j));
                            addClause(clause);
                        }

                    }
                }
            }

        }
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //  Methods for ternary constraints such as product and division.
    
    public void ternaryEncoding(ASTNode con, ASTNode auxVar) throws IOException {
        if (auxVar instanceof NumberConstant) {
            supportEncodingBinary(con, con.getChild(0), con.getChild(1), auxVar.getValue());
        }
        else {
            ternaryFunctionalEncoding(con, con.getChild(0), con.getChild(1), auxVar);
            
            // Alternative, larger encoding:
            // ternaryDirectEncoding(con, con.getChild(0), con.getChild(1), auxVar);
        }
    }
    
    public void ternaryDirectEncoding(ASTNode con, ASTNode node1, ASTNode node2, ASTNode node3) throws IOException
    {
        ArrayList<Intpair> dom1=getIntervalSetSAT(node1);
        ArrayList<Intpair> dom2=getIntervalSetSAT(node2);
        ArrayList<Intpair> dom3=getIntervalSetSAT(node3);
        
        for(Intpair pair1 : dom1) {
            for(long i=pair1.lower; i<=pair1.upper; i++) {
                for(Intpair pair2: dom2) {
                    for (long j=pair2.lower; j<=pair2.upper; j++) {
                        for(Intpair pair3: dom3) {
                            for(long k=pair3.lower; k<=pair3.upper; k++) {
                                if(!con.test(i, j, k)) {
                                    addClause((-node1.directEncode(this, i))+" "+(-node2.directEncode(this, j))+" "+(-node3.directEncode(this, k)));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    public void ternaryFunctionalEncoding(ASTNode con, ASTNode node1, ASTNode node2, ASTNode node3) throws IOException
    {
        ArrayList<Intpair> dom1=getIntervalSetSAT(node1);
        ArrayList<Intpair> dom2=getIntervalSetSAT(node2);
        
        for(Intpair pair1 : dom1) {
            for(long i=pair1.lower; i<=pair1.upper; i++) {
                for(Intpair pair2: dom2) {
                    for (long j=pair2.lower; j<=pair2.upper; j++) {
                        long k=con.func(i,j);
                        addClause((-node1.directEncode(this, i))+" "+(-node2.directEncode(this, j))+" "+(node3.directEncode(this, k)));
                    }
                }
            }
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //  Support encoding for binary and reified binary constraints.
    
    public void supportEncodingBinary(ASTNode constraint, ASTNode node1, ASTNode node2) throws IOException
    {
        supportEncodingBinaryGenerateClauses(constraint, node1, node2, false, false, 0);      
        supportEncodingBinaryGenerateClauses(constraint, node2, node1, true, false, 0);
    }
    
    //  This one calls the three- argument constraint.test function, using auxVal as the final argument. 
    public void supportEncodingBinary(ASTNode constraint, ASTNode node1, ASTNode node2, long auxVal) throws IOException
    {
        supportEncodingBinaryGenerateClauses(constraint, node1, node2, false, true, auxVal);
        supportEncodingBinaryGenerateClauses(constraint, node2, node1, true, true, auxVal);
    }
    
    private void supportEncodingBinaryGenerateClauses(ASTNode constraint, ASTNode node1, ASTNode node2, boolean reverse, boolean threeargs, long thirdarg) throws IOException
    {
        // Reverse indicates that the arguments should be swapped for constraint.test(....)
        ArrayList<Intpair> domain1=getIntervalSetSAT(node1);
        ArrayList<Intpair> domain2=getIntervalSetSAT(node2);
        
        for(Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                String clause=(-node1.directEncode(this,i)) + " ";
                
                for (Intpair pair2: domain2)
                {
                    for (long j=pair2.lower; j<=pair2.upper; j++)
                    {
                        if( (!threeargs && ((! reverse && constraint.test(i,j)) || (reverse && constraint.test(j,i)))) ||
                            (threeargs && ((!reverse && constraint.test(i,j,thirdarg)) || (reverse && constraint.test(j,i,thirdarg)))) ) {
                            clause+=node2.directEncode(this,j) + " ";
                        }
                    }
                }
                addClause(clause);
            }
        }
    }
    
    public void supportEncodingBinaryWithAuxVar(ASTNode constraint, ASTNode node1, ASTNode node2, long auxVarValue) throws IOException
    {
        //If either of the nodes in this binary constraint are a Constant value resort to using the Direct Encoding
        if (node1.getCategory()==ASTNode.Constant || node2.getCategory()==ASTNode.Constant){
           // System.out.println("Resorting to the Direct Encoding for this constraint : " + constraint.toString());
            directEncodingWithAuxVar(constraint,node1,node2,auxVarValue);
        }else {   
            supportEncodingBinaryGenerateClausesWithAuxVar(constraint, node1, node2, auxVarValue, false);      
            supportEncodingBinaryGenerateClausesWithAuxVar(constraint, node2, node1, auxVarValue, true);
        }
    }
    
    private void supportEncodingBinaryGenerateClausesWithAuxVar(ASTNode constraint, ASTNode node1, ASTNode node2, long auxVarValue, boolean reverse) throws IOException
    {
        // Reverse indicates that the arguments should be swapped for constraint.test(....)
        ArrayList<Intpair> domain1=getIntervalSetSAT(node1);
        ArrayList<Intpair> domain2=getIntervalSetSAT(node2);
        
        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                ArrayList<Long> supportClause=new ArrayList<Long>();
                supportClause.add(auxVarValue);
                supportClause.add(-node1.directEncode(this,i));
                
                ArrayList<Long> conflictClause=new ArrayList<Long>();
                
                conflictClause.add(-auxVarValue);
                conflictClause.add(-node1.directEncode(this, i));
                
                for (Intpair pair2: domain2)
                {
                    for (long j=pair2.lower; j<=pair2.upper; j++)
                    {
                        if( (!reverse && constraint.test(i,j)) || (reverse && constraint.test(j,i)) )
                        {
                            conflictClause.add(node2.directEncode(this,j));
                        }
                        else {
                            supportClause.add(node2.directEncode(this,j));
                        }
                    }
                }
                addClause(supportClause);
                addClause(conflictClause);
            }
        }
    }
    
    //  Two following methods are highly specific to < and <=
    public void orderEncoding(ASTNode constraintNode, ASTNode node1, ASTNode node2) throws IOException
    {
        ArrayList<Intpair> domain1=getIntervalSetSAT(node1);
        ArrayList<Intpair> domain2=getIntervalSetSAT(node2);
        
        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                loop2:
                for (int k=domain2.size()-1; k>=0; k--)
                {
                    Intpair pair2=domain2.get(k);
                    for (long j=pair2.upper; j>=pair2.lower; j--)
                    {
                        if (!constraintNode.test(i,j))
                        {
                            String clause="";
                            
                            clause+=node1.orderEncode(this,i-1) + " " + -node2.orderEncode(this,j);
                            addClause(clause);
                            break loop2;
                        }
                    }

                }

            }
        }
        
    }

    public void orderEncodingWithAuxVar(ASTNode constraintNode, ASTNode node1, ASTNode node2, long auxVarValue) throws IOException
    {
        ArrayList<Intpair> domain1=getIntervalSetSAT(node1);
        ArrayList<Intpair> domain2=getIntervalSetSAT(node2);
        
        for (Intpair pair1 : domain1)
        {
            for (long i=pair1.lower; i<=pair1.upper; i++)
            {
                loop2:
                for (int k=domain2.size()-1; k>=0; k--)
                {
                    Intpair currentPair=domain2.get(k);

                    for (long j=currentPair.upper; j>=currentPair.lower; j--)
                    {
                        if (!constraintNode.test(i,j))
                        {
                            String conflictClause="";
                            String supportClause="";

                            conflictClause+=(-auxVarValue) + " " + node1.orderEncode(this,i-1) + " " + -node2.orderEncode(this,j);
                            supportClause+=auxVarValue + " " + -node1.orderEncode(this,i-1) + " " + node2.orderEncode(this,j);
                            addClause(supportClause);
                            addClause(conflictClause);
                            break loop2;
                        }
                    }
                }
            }
        }
    }
    
    public long getNumVars() {
        return variableNumber-1;
    }
    public long getNumClauses() {
        return numClauses;
    }
}
