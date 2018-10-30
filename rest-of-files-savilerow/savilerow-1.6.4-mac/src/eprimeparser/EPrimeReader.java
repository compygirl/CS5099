package savilerow.eprimeparser;
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
import savilerow.model.*;

import java.util.*;
import java.io.*;
import java.lang.*;
import java.lang.reflect.*;

// EPrimeReader.java

public final class EPrimeReader {
    private EPrimeTokenizer tokens;

    private static final boolean VB_MTDS = false;

    static String[] ops = {"+", "-", "/", "*", "**", "%", "\\/", "/\\", "=>", "->", "<=>", "<->", "=", "!=", "<=", "<", ">=", ">", "<lex", "<=lex", ">lex", ">=lex", "union", "intersect", "in"};
    static String[] keywds = { "forall", "forAll", "exists", "sum", "such", "that", "letting", "given", "where", "find", "language", "int", "bool", "union", "intersect", "in", "false", "true" };
    
    private SymbolTable global_symbols;    // Only to be used when constructing Identifiers.

    private ASTNode branchingon;
    private String heuristic;

    private HashSet<String> binops;    // set of binary operator strings created in ctor.

    private HashSet<String> keywords;
    
    private HashMap<String, FunctionDescription> funcs;
    
    
    /* ====================================================================
     constructor
    ==================================================================== */
    public EPrimeReader(String fn) {
        tokens = new EPrimeTokenizer(fn);
        
        binops = new HashSet<String>(Arrays.asList(ops));
        global_symbols = new SymbolTable();
        
        keywords = new HashSet<String>(Arrays.asList(keywds));
        
        funcs=new HashMap<String, FunctionDescription>();
        funcs.put("toSet", new FunctionDescription("ToSet", 1));
        funcs.put("popcount", new FunctionDescription("Popcount", 1));
        funcs.put("toInt", new FunctionDescription("ToInt", 1));
    }

    // Version takes a string instead of a filename
    public EPrimeReader(String contents, boolean junk) {
        assert junk;
        tokens = new EPrimeTokenizer(contents, true);
        
        binops = new HashSet<String>(Arrays.asList(ops));
        global_symbols = new SymbolTable();
        
        keywords = new HashSet<String>(Arrays.asList(keywds));
        
        funcs=new HashMap<String, FunctionDescription>();
        funcs.put("toSet", new FunctionDescription("ToSet", 1));
        funcs.put("popcount", new FunctionDescription("Popcount", 1));
        funcs.put("toInt", new FunctionDescription("ToInt", 1));
    }

    /* ====================================================================
     read
    ==================================================================== */
    public Model readModel() {
        ASTNode constraints = null;
        ASTNode objective = null;
        try {
            tokens.mark("model1");            // This is not necessary, but puts in extra checks on reset stack.
            readHeader();
            tokens.commit("Read header line successfully.");
            readDeclarations();
            try {
                tokens.mark("model2");
                objective = readObjective();
                tokens.eraseMark("model2");
            } catch (EPrimeSyntaxException e) {
                tokens.reset("model2");
            }
            constraints = readConstraints();
            tokens.eraseMark("model1");
        } catch (EPrimeSyntaxException e) {
            tokens.reset("model1");
            CmdFlags.errorExit("Failed to read header line. Expected the following:  language ESSENCE' 1.0");
        }
        return new Model(constraints, global_symbols, objective, branchingon, heuristic);
    }

    /* ====================================================================
     Read a parameter file. Requires the model that was constructed
     when the model file was read. 
    ==================================================================== */

    public ArrayList<ASTNode> readParameterFile(Model m) {
        global_symbols = m.global_symbols;
        branchingon = null;
        heuristic = null;
        
        // Read the optional language line. 
        try {
            tokens.mark("paramhead");
            readHeader();
            tokens.eraseMark("paramhead");
            tokens.commit("Successfully parsed language line.");
        }
        catch (EPrimeSyntaxException e) {
            tokens.reset("paramhead");
        }
        
        
        ArrayList<ASTNode> paramlist = new ArrayList<ASTNode>();
        
        while (true) {
            try {
                tokens.mark("param");
                
                try {
                    tokens.mark("rpf");
                    ASTNode a = readLetting();
                    paramlist.add(a);
                    tokens.eraseMark("rpf");
                    tokens.commit("Successfully parsed letting statement.");
                } catch (EPrimeSyntaxException e) {
                    tokens.reset("rpf");
                    try {
                        tokens.mark("rpf2");
                        readHeuristic();
                        tokens.eraseMark("rpf2");
                        tokens.commit("Successfully parsed heuristic.");
                    } catch (EPrimeSyntaxException e2) {
                        tokens.reset("rpf2");
                        readBranchingOn();
                        tokens.commit("Successfully parsed branching on statement.");
                    }
                }
                
                tokens.eraseMark("param");
            } catch (EPrimeSyntaxException e2) {
                tokens.reset("param");
                break;
            }
        }
        
        // Try to read the next token and check if it is EOF. 
        try {
            tokens.nextToken();
            if (tokens.tokenType == EPrimeTokenizer.TT_EOF) {
                if (VB_MTDS) {
                    System.out.println("parameters read successfully");
                }
                
                if (heuristic != null) {
                    if (m.heuristic != null) {
                        CmdFlags.println("WARNING: Parameter file overrides 'heuristic' statement in model file.");
                    }
                    m.heuristic = heuristic;
                }
                if (branchingon != null) {
                    if (m.branchingon != null) {
                        CmdFlags.println("WARNING: Parameter file overrides 'branching on' statement in model file.");
                    }
                    m.branchingon = branchingon;
                }
                return paramlist;
            }
        }
        catch (EPrimeSyntaxException e2) {
        }
        
        // If next token is not EOF, or failed to read the next token, create an error message and quit. 
        tokens.raiseError();
        return null;
    }

    // Header & Declarations %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    /* ====================================================================
     readHeader()
	 Header ::= ESSENCE' <Int>.<Int>
	 NB This disallows the use of true/false as 1/0 in this position
    ==================================================================== */
    private void readHeader() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read header line");
        }
        readTerminalString("language");
        try {
            tokens.mark("ess");
            readTerminalString("ESSENCE'");
            tokens.eraseMark("ess");
        } catch (EPrimeSyntaxException Ozgur) {
            tokens.reset("ess");
            readTerminalString("Essence");
        }
        readInt();
        readTerminalString(".");
        readInt();
        if (VB_MTDS) {
            System.out.println("header line read successfully");
        }
    }

    /* ====================================================================
     readDeclarations()
     (<Given> | <Where> | <Letting> | <Find>)*
    ==================================================================== */
    private void readDeclarations() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read declarations");
        }
        while (true) {
            try {
                readDeclaration();
                tokens.commit("Successfully parsed declaration.");
            } catch (EPrimeSyntaxException e) {
                if (VB_MTDS) {
                    System.out.println("finished reading declarations");
                }
                return; 
            }
        }
    }

    /* ====================================================================
     readDeclaration()
     (<Given> | <Where> | <Letting> | <Find>)
    ==================================================================== */
    private void readDeclaration() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read a declaration");
        }
        try {
            tokens.mark();
            readGiven();
            tokens.eraseMark();
        } catch (EPrimeSyntaxException e2) {
            tokens.reset();
            try {
                tokens.mark();
                readWhere();
                tokens.eraseMark();
            } catch (EPrimeSyntaxException e3) {
                tokens.reset();
                try {
                    tokens.mark();
                    ASTNode let = readLetting();
                    tokens.eraseMark();
                    global_symbols.lettings_givens.add(let);
                } catch (EPrimeSyntaxException e4) {
                    tokens.reset();
                    try {
                        tokens.mark();
                        readFind();
                        tokens.eraseMark();
                    } catch (EPrimeSyntaxException e5) {
                        tokens.reset();
                        try {
                            tokens.mark();
                            readBranchingOn();
                            tokens.eraseMark();
                        } catch (EPrimeSyntaxException e6) {
                            tokens.reset();
                            try {
                                tokens.mark();
                                readHeuristic();
                                tokens.eraseMark();
                            } catch (EPrimeSyntaxException e7) {
                                tokens.reset();
                                throw new EPrimeSyntaxException("Given, Where, Letting, Find, Branching on, Heuristic", "expression beginning: " + tokens.toString());
                            }
                        }
                    }
                }
            }
        }
        if (VB_MTDS) {
            System.out.println("declaration read successfully");
        }
    }

    /* ====================================================================
     readGiven()
     <Given> ::= "given" <IdList> : <Domain>
    ==================================================================== */
    private void readGiven() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read given");
        }
        readTerminalString("given");
        tokens.commit("Parsed 'given' keyword.");
        ASTNode a = readIdList();

        try {
            tokens.mark("given");
            readTerminalString(":");
            tokens.commit("Parsed 'given ... :'.");
            ASTNode d = readDomain();

            ArrayList<ASTNode> ids = a.getChildren();
            for (ASTNode id : ids) {
                global_symbols.lettings_givens.add(new Given(id, d));
            }
            tokens.eraseMark("given");
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("given");
            readTerminalString("new");
            readTerminalString("domain");
            readTerminalString("int");

            tokens.commit("Successfully parsed 'given ... new domain int'.");

            ArrayList<ASTNode> ids = a.getChildren();
            for (ASTNode id : ids) {
                global_symbols.lettings_givens.add(new Given(id, new IntegerDomain(new Range(null, null))));
            }
        }

        if (VB_MTDS) {
            System.out.println("given read successfully");
        }
    }

    /* ====================================================================
     readWhere()
	 where Expression
    ==================================================================== */
    private void readWhere() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read where");
        }
        readTerminalString("where");
        tokens.commit("Successfully parsed 'where' keyword.");
        ASTNode a = readExpression();
        if (VB_MTDS) {
            System.out.println("where read successfully");
        }
        global_symbols.lettings_givens.add(new Where(a));
    }

    /* ====================================================================
     readLetting()
     "letting" Identifier "be" "domain" Domain   |
     "letting" Identifier [ ":" Domain ] "be" Expression 
    ==================================================================== */
    private ASTNode readLetting() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read letting");
        }
        readTerminalString("letting");
        tokens.commit("Parsed 'letting' keyword.");
        ASTNode id = readIdentifier();
        tokens.commit("Parsed 'letting' keyword and identifier.");
        try {
            tokens.mark("rl");
            readTerminalString("be");
            readTerminalString("domain");
            tokens.commit("Successfully parsed 'letting ... be domain'.");
            ASTNode eq = readDomain();
            tokens.eraseMark("rl");
            if (VB_MTDS) {
                System.out.println("letting read successfully");
            }
            return new Letting(id, eq);
        } catch (EPrimeSyntaxException e) {
            tokens.reset("rl");

            // Parse optional domain specified with a :
            ASTNode dom;
            try {
                tokens.mark("rldom");
                readTerminalString(":");
                tokens.commit("Successfully parsed 'letting ... :'.");
                dom = readDomain();
                tokens.eraseMark("rldom");
            } catch (EPrimeSyntaxException e2) {
                tokens.reset("rldom");
                dom = null;
            }

            // New bit.
            try {
                tokens.mark("rlp");
                readTerminalString("be");
                tokens.eraseMark("rlp");
            } catch (EPrimeSyntaxException e1) {
                tokens.reset("rlp");
                readTerminalString("=");
            }

            tokens.commit("Successfully parsed 'letting ... be'.");

            // Optimisation -- first try the quick parser for big matrices of ints
            ASTNode eq = null;

            try {
                tokens.mark("quick");
                eq = readQuickConstantMatrixNotDeref();
                tokens.eraseMark("quick");
            } catch (EPrimeSyntaxException e1) {
                tokens.reset("quick");
            }

            if (eq == null) {
                eq = readExpression();
            }            // the general case.
            
            tokens.commit("Letting statement read successfully");
            
            if (VB_MTDS) {
                System.out.println("letting read successfully");
            }
            if (dom == null) {
                return new Letting(id, eq);
            } else {
                return new Letting(id, eq, dom);
            }
        }
    }

    /* ====================================================================
     readFind()
     <Find> ::= "find" <IdList> : { <Domain> | <Identifier> }
    ==================================================================== */
    private void readFind() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read find");
        }
        readTerminalString("find");
        tokens.commit("Parsed 'find' keyword.");
        ASTNode a = readIdList();
        tokens.commit("Parsed 'find' keyword and list of identifiers.");
        readTerminalString(":");
        tokens.commit("Parsed 'find ... :'.");
        ASTNode b = readDomain();        // will also accept an Identifier

        ArrayList<ASTNode> ids = a.getChildren();
        for (ASTNode id : ids) {
            global_symbols.lettings_givens.add(new Find(id, b.copy()));
        }

        if (VB_MTDS) {
            System.out.println("find read successfully");
        }
    }

    /* ====================================================================
     readBranchingOn()
     <BranchingOn> ::= "branching" "on" "["  "]"
    ==================================================================== */
    private void readBranchingOn() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read branching on");
        }
        readTerminalString("branching");
        readTerminalString("on");
        tokens.commit("Parsed 'branching on'.");

        readTerminalString("[");
        tokens.commit("Parsed 'branching on ['.");
        ArrayList<ASTNode> bran = new ArrayList<ASTNode>();

        while (true) {
            try {
                tokens.mark("bo");
                bran.add(readPartExpression());
                tokens.eraseMark("bo");
            } catch (EPrimeSyntaxException e) {
                tokens.reset("bo");
                break;
            }
            try {
                tokens.mark("comma");
                readTerminalString(",");
                tokens.eraseMark("comma");
            } catch (EPrimeSyntaxException e3) {
                tokens.reset("comma");
                break;                // If there's no comma, jump out. Allows a final comma before the square bracket.
            }
        }

        readTerminalString("]");

        for (int i =0; i < bran.size(); i++) {
            if (bran.get(i).getDimension() == 0) {
                ArrayList<ASTNode> tmp = new ArrayList<ASTNode>();
                tmp.add(bran.get(i));
                bran.set(i, new CompoundMatrix(tmp));
            }
            bran.set(i, new Flatten(bran.get(i)));
        }

        if (VB_MTDS) {
            System.out.println("branching on read successfully");
        }
        if (branchingon != null) {
            // Not really a syntax error
            throw new EPrimeSyntaxException("At most one 'branching on' statement.", "A second 'branching on' statement.");
        }
        branchingon = new Concatenate(bran);
    }

    /* ====================================================================
     readHeuristic()
     <Heuristic> ::= "heuristic" [ "sdf",  ]
    ==================================================================== */
    private void readHeuristic() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read heuristic");
        }
        readTerminalString("heuristic");
        tokens.commit("Successfully parsed 'heuristic' keyword.");
        String h = readTerminalString();
        String h2 = null;
        if (h.equals("static")) { h2 = "STATIC"; } else if (h.equals("sdf")) { h2 = "SDF"; } else if (h.equals("conflict")) { h2 = "CONFLICT"; } else if (h.equals("srf")) { h2 = "SRF"; }

        if (h2 == null) {
            throw new EPrimeSyntaxException("static, sdf, conflict or srf", h);
        }

        if (heuristic != null) {
            // Not really a syntax error
            throw new EPrimeSyntaxException("At most one 'heuristic' statement.", "A second 'heuristic' statement.");
        }
        heuristic = h2;
    }

    /* ====================================================================
     readDomain()
	 
     Either a simple domain or a matrix domain.
     
     Have to try matrix domain first, otherwise the word 'matrix' will be parsed
     as an identifier by readSimpleDomain. 
     
     Brackets taken out here. 
    ==================================================================== */
    private ASTNode readDomain() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read Domain");
        }
        try {
            tokens.mark("dom");
            ASTNode a = readMatrixDomain();
            tokens.eraseMark("dom");
            return a;
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("dom");
            return readSimpleDomain();
        }
    }

    /* ====================================================================
     readSimpleDomain()
	 SimpleDomain -> "bool"
	     | "int" [ "(" BoundedSetOfRanges ")" ]
	     | IdentifierString
	     
	     | SimpleDomain "intersect" SimpleDomain
	     | SimpleDomain "union" SimpleDomain
	     | SimpleDomain "-" SimpleDomain
	     | "(" SimpleDomain ")"
	 
	 Left-recursive rules implemented by reading a sequence. 
	     
    ==================================================================== */

    private ASTNode readSimpleDomain() throws EPrimeSyntaxException {
        // Mirrors readExpression.
        ArrayList<ASTNode> exlist = new ArrayList<ASTNode>();        // List of expressions [Part,BinOp, Part ....].
        exlist.add(readPartSimpleDomain());
        while (true) {
            try {
                tokens.mark("rsd");
                if (VB_MTDS) {
                    System.out.println("attempting to read a binop then partexpression");
                }
                ASTNode a = readSimpleDomainBinOp();
                ASTNode b = readPartSimpleDomain();
                tokens.eraseMark("rsd");

                exlist.add(a); exlist.add(b);                // Make sure both are read successfully before adding them to list.
            } catch (EPrimeSyntaxException e) {
                tokens.reset("rsd");
                break;
            }
        }
        if (VB_MTDS) {
            System.out.println("succeeded reading SimpleDomain");
        }
        if (VB_MTDS) {
            System.out.println("Converting list:" + exlist);
        }

        ShuntingYard sy = new ShuntingYard();
        ASTNode a = sy.convertToTree(exlist);
        if (VB_MTDS) {
            System.out.println("Converted to tree:" + a);
        }
        return a;
    }

    private ASTNode readPartSimpleDomain() throws EPrimeSyntaxException {
        try {
            tokens.mark("atom");
            ASTNode d = readSimpleDomainAtom();
            tokens.eraseMark("atom");
            return d;
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("atom");

            // Read a bracketed expresssion.
            readTerminalString("(");
            ASTNode d = readSimpleDomain();
            readTerminalString(")");
            return d;
        }
    }

    /* ====================================================================
     readSimpleDomainAtom()
	 SimpleDomain -> "bool"
	     | "int" [ "(" BoundedSetOfRanges ")" ]
	     | IdentifierString
	     
     Read a SimpleDomain that is atomic as far as domains are concerned.
     e.g. int(1, 3..5) or int() (which means the empty domain).
    ==================================================================== */

    private ASTNode readSimpleDomainAtom() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read SimpleDomainAtom");
        }

        try {
            tokens.mark("rsd");
            readTerminalString("bool");
            tokens.eraseMark("rsd");
            return new BooleanDomain();
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("rsd");
            try {
                tokens.mark("rsd2");
                readTerminalString("int");
                try {
                    tokens.mark("rsd3");
                    readTerminalString("(");

                    ASTNode a;
                    /*                try{
                    tokens.mark("rsd4");
                    a=readBoundedSetOfRanges();
                    tokens.eraseMark("rsd4");
                }
                catch(EPrimeSyntaxException e) {
                    tokens.reset("rsd4");*/
                    try {
                        tokens.mark("rsd5");
                        a = readUnboundedSetOfRanges();
                        tokens.eraseMark("rsd5");
                    } catch (EPrimeSyntaxException e2) {
                        tokens.reset("rsd5");
                        a = new IntegerDomain(new EmptyRange());                        /// Empty domain! "int()".
                    }
                    // }
                    readTerminalString(")");
                    tokens.eraseMark("rsd3");
                    tokens.eraseMark("rsd2");
                    return a;
                } catch (EPrimeSyntaxException e) {
                    tokens.reset("rsd3");
                }
                tokens.eraseMark("rsd2");
                return new IntegerDomain(new Range(null, null));                // Just "int"
            } catch (EPrimeSyntaxException e2) {
                tokens.reset("rsd2");
                ASTNode st = readIdentifier();
                return st;
            }
        }
    }

    // read intersect, union or -
    private ASTNode readSimpleDomainBinOp() throws EPrimeSyntaxException {
        if (tryReadTerminalString("intersect")) {
            return new BinOpPlaceholder("intersect");
        } else if (tryReadTerminalString("union")) {
            return new BinOpPlaceholder("union");
        } else if (tryReadTerminalString("-")) {
            return new BinOpPlaceholder("-");
        } else {
            throw new EPrimeSyntaxException("intersect, union or -", "");
        }
    }

    private ASTNode readMatrixDomain() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read MatrixDomain");
        }
        ArrayList<ASTNode> indices = new ArrayList<ASTNode>();
        readTerminalString("matrix");
        tokens.commit("Successfully parsed 'matrix' keyword.");
        readTerminalString("indexed");
        readTerminalString("by");
        tokens.commit("Successfully parsed 'matrix indexed by'.");
        readTerminalString("[");
        indices.add(readDomain());
        tokens.commit("Parsed 'matrix indexed by [...'.");
        while (true) {
            try {
                tokens.mark("rmdom");
                readTerminalString(",");
                tokens.commit("Parsed 'matrix indexed by [... ,'.");
                ASTNode a = readDomain();
                tokens.commit("Parsed 'matrix indexed by [...'.");
                tokens.eraseMark("rmdom");
                indices.add(a);
            } catch (EPrimeSyntaxException e1) {
                tokens.reset("rmdom");
                break;
            }
        }

        readTerminalString("]");
        readTerminalString("of");
        tokens.commit("Successfully parsed 'matrix indexed by ... of'.");
        ASTNode base = readSimpleDomain();
        
        if (VB_MTDS) System.out.println("MatrixDomain read successfully");
        return new MatrixDomain(base, indices);
    }

    // Objective & Constraints %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    /* ====================================================================
     readObjective()
	 Objective ::= maximising <ArithExpr> | minimising <ArithExpr>
    ==================================================================== */
    private ASTNode readObjective() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read objective");
        }
        ASTNode ob = null;
        try {
            tokens.mark();
            ob = readMaximising();
            tokens.eraseMark();
        } catch (EPrimeSyntaxException e) {
            tokens.reset();
            try {
                tokens.mark();
                ob = readMinimising();
                tokens.eraseMark();
            } catch (EPrimeSyntaxException e2) {
                tokens.reset();
                throw new EPrimeSyntaxException("Objective", tokens.toString());
            }
        }
        if (VB_MTDS) {
            System.out.println("objective read successfully: " + ob);
        }
        return ob;
    }

    /* ====================================================================
     readMinimising()
     <Minimising> ::= "minimising" <AtomicId>
    ==================================================================== */
    private ASTNode readMinimising() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read minimizing");
        }
        readTerminalString("minimising");
        tokens.commit("Successfully parsed 'minimising' keyword.");
        ASTNode a = readExpression();
        if (VB_MTDS) {
            System.out.println("read minimizing successfully");
        }
        return new Minimising(a);
    }

    /* ====================================================================
     readMaximising()
     <maximising> ::= "maximising" <ArithmeticExpression>
    ==================================================================== */
    private ASTNode readMaximising() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read maximizing");
        }
        readTerminalString("maximising");
        tokens.commit("Successfully parsed 'maximising' keyword.");
        ASTNode a = readExpression();
        if (VB_MTDS) {
            System.out.println("read maximizing successfully");
        }
        return new Maximising(a);
    }

    /* ====================================================================
     readConstraints()
	 <Constraints> ::= "such that" {<Constraint>}*
    ==================================================================== */
    private ASTNode readConstraints() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read constraints");
        }
        readTerminalString("such");
        readTerminalString("that");
        tokens.commit("Successfully parsed 'such that'.");
        // Make a single And for all the constraints.

        ArrayList<ASTNode> andlist = new ArrayList<ASTNode>();
        try {
            tokens.mark("con1");
            andlist.add(readExpression());
            tokens.eraseMark("con1");
            while (true) {
                try {
                    tokens.mark("con2");
                    readTerminalString(",");
                    tokens.commit("Parsed comma.");
                    andlist.add(readExpression());
                    tokens.eraseMark("con2");
                } catch (EPrimeSyntaxException e2) {
                    tokens.reset("con2");
                    break;
                }
            }
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("con1");
        }

        tokens.nextToken();
        if (tokens.tokenType == EPrimeTokenizer.TT_EOF) {
            if (VB_MTDS) {
                System.out.println("constraints read successfully");
            }
            return new Top(new And(andlist));            // Wrap it in a Top.
        }
        throw new EPrimeSyntaxException("constraint", tokens.toString());

    }

    // Expressions %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    /* ====================================================================
     readExpression()
     Expression -> PartExpression {BinOp PartExpression}*
    ==================================================================== */

    private ASTNode readExpression() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read Expression");
        }
        // List of expressions [Part,BinOp, Part ....].
        ArrayList<ASTNode> exlist = new ArrayList<ASTNode>();
        // ASTNode first=readPartExpression();
        exlist.add(readPartExpression());
        tokens.commit("Successfully read part expression:" + exlist.get(0));
        while (true) {
            try {
                tokens.mark("inreadexpression");
                if (VB_MTDS) {
                    System.out.println("attempting to read a binop then partexpression");
                }
                ASTNode a = readBinOp();
                tokens.commit("Successfully read binop:" + a);
                ASTNode b = readPartExpression();
                tokens.commit("Successfully read part expression:" + b);
                if (VB_MTDS) {
                    System.out.println("succeeded read a binop then partexpression " + a + ", " + b);
                }
                tokens.eraseMark("inreadexpression");
                exlist.add(a); exlist.add(b);                // Make sure both are read successfully before adding them to list.
            } catch (EPrimeSyntaxException e) {
                tokens.reset("inreadexpression");
                break;
            }
        }
        if (VB_MTDS) {
            System.out.println("succeeded reading Expression");
        }
        // return new Expression(exlist);
        if (VB_MTDS) {
            System.out.println("Converting list:" + exlist);
        }
        ShuntingYard sy = new ShuntingYard();
        ASTNode a = sy.convertToTree(exlist);
        if (VB_MTDS) {
            System.out.println("Converted to tree:" + a);
        }
        return a;
    }

    /* ====================================================================
    readPartExpression()
    "(" Expression ")"  |
    UnaryExpression |
    GlobalConstraint |
    QuantifiedExpression |
    ArrayExpression | 
    AtomicExpression 
    
    
    ==================================================================== */

    private ASTNode readPartExpression() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read PartExpression");
        }
        ASTNode a;
        try {
            tokens.mark("rpe");
            a = readBracketedExpression();
            tokens.eraseMark("rpe");
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("rpe");
            try {
                tokens.mark("rpe2");
                a = readUnaryExpression();
                tokens.eraseMark("rpe2");
            } catch (EPrimeSyntaxException e2) {
                tokens.reset("rpe2");
                try {
                    tokens.mark("rpe3");
                    a = readConstant();
                    tokens.eraseMark("rpe3");
                } catch (EPrimeSyntaxException e3) {
                    tokens.reset("rpe3");
                    try {
                        tokens.mark("rpe4");
                        a = readGlobalConstraint();
                        tokens.eraseMark("rpe4");
                    } catch (EPrimeSyntaxException e4) {
                        tokens.reset("rpe4");
                        try {
                            tokens.mark("rpe5");
                            a = readQuantifiedExpression();
                            tokens.eraseMark("rpe5");
                        } catch (EPrimeSyntaxException e5) {
                            tokens.reset("rpe5");
                            try {
                                tokens.mark("rpe6");
                                a = readArrayExpression();   // Includes bare identifier, matrix deref, matrix slice.
                                tokens.eraseMark("rpe6");
                            } catch (EPrimeSyntaxException e6) {
                                tokens.reset("rpe6");
                                try {
                                    tokens.mark("rpe7");
                                    a = readBubble();
                                    tokens.eraseMark("rpe7");
                                } catch (EPrimeSyntaxException e7) {
                                    tokens.reset("rpe7");
                                    a=readSimpleDomain();
                                }
                            }
                        }
                    }
                }
            }
        }

        if (VB_MTDS) {
            System.out.println("succeeded reading PartExpression");
        }
        return a;
    }
    
    private ASTNode readBracketedExpression() throws EPrimeSyntaxException {
        readTerminalString("(");
        ASTNode a=readExpression();
        readTerminalString(")");
        
        //  In case it is a bracketed matrix expression, it may be followed by a slice or deref. 
        try {
            tokens.mark("slice");
            a = readSliceOrDeref(a);
            tokens.eraseMark("slice");
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("slice");
        }
        return a;
    }
    
    /* ====================================================================
	 readUnaryExpression()
	 "!" Expression  | "-" Expression  | "|" Expression "|"  
    ==================================================================== */
    private ASTNode readUnaryExpression() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read UnaryExpression");
        }
        try {
            tokens.mark();
            readTerminalString("!");
            tokens.commit("Read '!' (negation)");
            ASTNode a = readPartExpression();
            tokens.eraseMark();
            return new Negate(a);
        } catch (EPrimeSyntaxException e1) {
            tokens.reset();
            try {
                tokens.mark("unaryminus");
                readTerminalString("-");
                tokens.commit("Read '-' (unary minus)");
                ASTNode a = readPartExpression();

                // One attempt to make unary minus work correctly with power. Parse all powers after the unary minus and build the tree here.
                // Only works because power has the highest precedence of all the binary operators. Otherwise it would interfere with parsing other operators.

                ArrayList<ASTNode> listpow = new ArrayList<ASTNode>();

                while (true) {
                    try {
                        tokens.mark("trypow");
                        readTerminalString("**");
                        tokens.commit("Read '**' (power)");
                        listpow.add(readPartExpression());
                        tokens.eraseMark("trypow");
                    } catch (EPrimeSyntaxException epow) {
                        tokens.reset("trypow");
                        break;                        // exit the loop.
                    }
                }
                tokens.eraseMark("unaryminus");
                if (listpow.size() > 0) {
                    // Build a tree of power
                    ASTNode tree = listpow.get(listpow.size() - 1);
                    listpow.remove(listpow.size() - 1);

                    while (listpow.size() > 0) {
                        tree = new Power(listpow.get(listpow.size() - 1), tree);
                        listpow.remove(listpow.size() - 1);
                    }

                    tree = new Power(a, tree);
                    return new UnaryMinus(tree);

                } else {
                    return new UnaryMinus(a);                    // The simple case.
                }
            } catch (EPrimeSyntaxException e2) {
                tokens.reset("unaryminus");
                readTerminalString("|");
                tokens.commit("Read '|' (absolute value)");
                ASTNode a = readExpression();
                readTerminalString("|");
                return new Absolute(a);
            }
        }
        // if (VB_MTDS) System.out.println("succeeded reading UnaryExpression") ;
    }

    /* ====================================================================
	 readBubble()
	   
    ==================================================================== */
    private ASTNode readBubble() throws EPrimeSyntaxException {
        try {
            tokens.mark("bubble-find");
            readTerminalString("{");

            ASTNode id = readIdentifier();

            readTerminalString("@");

            // horrible hack
            int i = global_symbols.lettings_givens.size();

            readFind();
            int j = global_symbols.lettings_givens.size();
            assert j == i + 1;

            ASTNode find = global_symbols.lettings_givens.removeLast();

            readTerminalString("such");
            readTerminalString("that");

            // Make a single And for all the constraints.

            ArrayList<ASTNode> andlist = new ArrayList<ASTNode>();

            andlist.add(readExpression());
            while (true) {
                try {
                    tokens.mark("con2");
                    readTerminalString(",");
                    andlist.add(readExpression());
                    tokens.eraseMark("con2");
                } catch (EPrimeSyntaxException e2) {
                    tokens.reset("con2");
                    break;
                }
            }
            readTerminalString("}");
            tokens.eraseMark("bubble-find");
            return new AuxBubble(id, find, new And(andlist));
        } catch (EPrimeSyntaxException e3) {
            tokens.reset("bubble-find");
            readTerminalString("{");
            ASTNode exp = readExpression();

            readTerminalString("@");
            readTerminalString("such");
            readTerminalString("that");

            ArrayList<ASTNode> andlist = new ArrayList<ASTNode>();

            andlist.add(readExpression());
            while (true) {
                try {
                    tokens.mark("con2");
                    readTerminalString(",");
                    andlist.add(readExpression());
                    tokens.eraseMark("con2");
                } catch (EPrimeSyntaxException e2) {
                    tokens.reset("con2");
                    break;
                }
            }
            readTerminalString("}");

            return new Bubble(exp, new And(andlist));
        }
    }

    /* ====================================================================
     readGlobalConstraint()
	 alldifferent "(" ArrayExpression ")"
     gcc "("    ")
     | table "(" ArrayExpression "," "[" Tuple "," Tuple ....  [","]  "]" ")"
     | atmost "(" ArrayExpression "," ArrayExpression "," ArrayExpression ")"
    ==================================================================== */
    private ASTNode readGlobalConstraint() throws EPrimeSyntaxException {
        try {
            tokens.mark();

            try {
                tokens.mark("alldiff");
                readTerminalString("alldifferent");
                tokens.eraseMark("alldiff");
            } catch (EPrimeSyntaxException a1) {
                tokens.reset("alldiff");
                readTerminalString("allDiff");
            }
            tokens.commit("Parsed keyword 'allDiff'");

            readTerminalString("(");
            ASTNode a = readArrayExpression();
            readTerminalString(")");
            tokens.eraseMark();
            return new AllDifferent(a);
        } catch (EPrimeSyntaxException e1) {
            tokens.reset();
            try {
                tokens.mark();
                readTerminalString("gcc");
                tokens.commit("Parsed keyword 'gcc'");
                readTerminalString("(");
                ASTNode array = readArrayExpression();
                readTerminalString(",");
                ASTNode index = readArrayExpression();
                readTerminalString(",");
                ASTNode result = readArrayExpression();
                readTerminalString(")");
                tokens.eraseMark();
                return new GlobalCard(array, index, result);
            } catch (EPrimeSyntaxException e2) {
                tokens.reset();
                try {
                    tokens.mark("readtab");
                    ASTNode a = readTableConstraint();
                    tokens.eraseMark("readtab");
                    return a;
                } catch (EPrimeSyntaxException e3) {
                    tokens.reset("readtab");
                    try {
                        tokens.mark();
                        readTerminalString("atmost");
                        tokens.commit("Parsed keyword 'atmost'");
                        readTerminalString("(");
                        ASTNode vars = readArrayExpression();
                        readTerminalString(",");
                        ASTNode occs = readArrayExpression();
                        readTerminalString(",");
                        ASTNode vals = readArrayExpression();
                        readTerminalString(")");
                        tokens.eraseMark();
                        return new AtMost(vars, occs, vals);
                    } catch (EPrimeSyntaxException e4) {
                        tokens.reset();
                        try {
                            tokens.mark();
                            readTerminalString("atleast");
                            tokens.commit("Parsed keyword 'atleast'");
                            readTerminalString("(");
                            ASTNode vars = readArrayExpression();
                            readTerminalString(",");
                            ASTNode occs = readArrayExpression();
                            readTerminalString(",");
                            ASTNode vals = readArrayExpression();
                            readTerminalString(")");
                            tokens.eraseMark();
                            return new AtLeast(vars, occs, vals);
                        } catch (EPrimeSyntaxException e5) {
                            tokens.reset();
                            try {
                                tokens.mark("ade");
                                readTerminalString("alldifferent_except");
                                tokens.commit("Parsed keyword 'alldifferent_except'");
                                readTerminalString("(");
                                ASTNode vars = readArrayExpression();
                                readTerminalString(",");
                                ASTNode exceptval = readExpression();
                                readTerminalString(")");
                                tokens.eraseMark("ade");
                                return new AllDifferentExcept(vars, exceptval);
                            } catch (EPrimeSyntaxException e6) {
                                tokens.reset("ade");
                                try {
                                    tokens.mark("func");
                                    ASTNode f = readFunction();
                                    tokens.eraseMark("func");
                                    return f;
                                } catch (EPrimeSyntaxException e7) {
                                    tokens.reset("func");
                                    try {
                                        tokens.mark("funcgeneric");
                                        ASTNode f2=readFunctionGeneric();
                                        tokens.eraseMark("funcgeneric");
                                        return f2;
                                    } catch (EPrimeSyntaxException e8) {
                                        tokens.reset("funcgeneric");
                                        throw new EPrimeSyntaxException("A global constraint", "");
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
    }

    private ASTNode readFunction() throws EPrimeSyntaxException {
        try {
            tokens.mark("min");
            readTerminalString("min");
            tokens.commit("Parsed keyword 'min'");
            tokens.eraseMark("min");

            readTerminalString("(");
            ASTNode a = readExpression();
            boolean comma = tryReadTerminalString(",");

            if (comma) {
                ASTNode b = readExpression();
                readTerminalString(")");
                return new Min(a, b);
            } else {
                readTerminalString(")");
                return new MinVector(a);
            }
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("min");
            try {
                tokens.mark("max");
                readTerminalString("max");
                tokens.commit("Parsed keyword 'max'");
                tokens.eraseMark("max");

                readTerminalString("(");
                ASTNode a = readExpression();
                boolean comma = tryReadTerminalString(",");

                if (comma) {
                    ASTNode b = readExpression();
                    readTerminalString(")");
                    return new Max(a, b);
                } else {
                    readTerminalString(")");
                    return new MaxVector(a);
                }
            } catch (EPrimeSyntaxException e2) {
                tokens.reset("max");
                try {
                    tokens.mark("fact");
                    readTerminalString("factorial");
                    tokens.commit("Parsed keyword 'factorial'");
                    readTerminalString("(");
                    ASTNode a = readExpression();
                    readTerminalString(")");
                    tokens.eraseMark("fact");
                    return new Factorial(a);
                } catch (EPrimeSyntaxException e4) {
                    tokens.reset("fact");

                    // Matrix product, sum, and, or.
                    try {
                        tokens.mark("prod");
                        readTerminalString("product");
                        tokens.commit("Parsed keyword 'product'");
                        readTerminalString("(");
                        ASTNode a = readArrayExpression();
                        readTerminalString(")");
                        tokens.eraseMark("prod");
                        return new TimesVector(a);
                    } catch (EPrimeSyntaxException e5) {
                        tokens.reset("prod");
                        // sum function.
                        try {
                            tokens.mark("sumvector");
                            readTerminalString("sum");
                            readTerminalString("(");
                            tokens.commit("Parsed keyword 'sum'");                            // Unusual one --- after bracket in case 'sum' belongs to a quantifier.
                            ASTNode a = readArrayExpression();
                            readTerminalString(")");
                            tokens.eraseMark("sumvector");
                            return new SumVector(a);
                        } catch (EPrimeSyntaxException e6) {
                            tokens.reset("sumvector");

                            try {
                                tokens.mark("andvector");
                                readTerminalString("and");
                                tokens.commit("Parsed keyword 'and'");
                                readTerminalString("(");
                                ASTNode a = readArrayExpression();
                                readTerminalString(")");
                                tokens.eraseMark("andvector");
                                return new AndVector(a);
                            } catch (EPrimeSyntaxException e7) {
                                tokens.reset("andvector");

                                readTerminalString("or");
                                tokens.commit("Parsed keyword 'or'");
                                readTerminalString("(");
                                ASTNode a = readArrayExpression();
                                readTerminalString(")");
                                return new OrVector(a);

                            }
                        }
                    }
                }
            }
        }
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //   Read functions where the name of the function
    //   is not overloaded (as, for example, min and max are). 
    
    private ASTNode readFunctionGeneric() throws EPrimeSyntaxException {
        tokens.mark("readfuncg");
        
        try {
            String s=readTerminalString();
            
            if(! funcs.containsKey(s)) {
                throw new EPrimeSyntaxException("Generic Function", "Unknown");
            }
            
            tokens.commit("Parsed function name "+s);
            FunctionDescription f=funcs.get(s);
            
            readTerminalString("(");
            tokens.commit("Parsed function name "+s+" and open bracket.");
            
            ASTNode slot1=readExpression();
            
            ASTNode slot2=null;
            ASTNode slot3=null;
            
            if(f.numArgs>1) {
                readTerminalString(",");
                slot2=readExpression();
                
                if(f.numArgs>2) {
                    readTerminalString(",");
                    slot3=readExpression();
                }
            }
            
            readTerminalString(")");
            
            ASTNode func=f.construct(slot1, slot2, slot3);
            
            tokens.eraseMark("readfuncg");
            
            return func;
        }
        catch (EPrimeSyntaxException e) {
            tokens.reset("readfuncg");
            throw e;
        }
    }
    
    private ASTNode readTableConstraint() throws EPrimeSyntaxException {
        readTerminalString("table");
        tokens.commit("Parsed keyword 'table'");
        readTerminalString("(");
        ASTNode vars = readArrayExpression();
        readTerminalString(",");
        tokens.commit("Parsed 'table(...,', expecting matrix of satisfying tuples.");
        ASTNode tups;
        try {
            // try to read a bracketed list of tuples with the old [<...>,<...>] syntax.
            tokens.mark();
            readTerminalString("[");
            // read any number of tuples.
            ArrayList<ASTNode> tuples = new ArrayList<ASTNode>();
            while (true) {
                try {
                    tokens.mark();
                    tuples.add(readTuple());
                    tokens.eraseMark();
                } catch (EPrimeSyntaxException e3) {
                    tokens.reset();
                    break;
                }
                try {
                    tokens.mark();
                    readTerminalString(",");
                    tokens.eraseMark();
                } catch (EPrimeSyntaxException e3) {
                    tokens.reset();
                    break;                    // If there's no comma, jump out. Allows a final comma before the square bracket.
                }
            }
            readTerminalString("]");
            tokens.eraseMark();
            tups = CompoundMatrix.makeCompoundMatrix(tuples);
        } catch (EPrimeSyntaxException e5) {
            tokens.reset();
            tups = readArrayExpression();
        }
        readTerminalString(")");
        return new Table(vars, tups);
    }

    /* ====================================================================
     readQuantifiedExpression()
     quantifier IdList ":" BoundedDomain "." expression
    ==================================================================== */
    private ASTNode readQuantifiedExpression() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read quantified expression");
        }
        int qtype;        // 1,2,3 for forall, exists, sum
        try {
            tokens.mark();

            // either read forall or forAll (new spelling)
            try {
                tokens.mark("forall");
                readTerminalString("forall");
                tokens.eraseMark("forall");
            } catch (EPrimeSyntaxException f1) {
                tokens.reset("forall");
                readTerminalString("forAll");
            }

            tokens.eraseMark();
            qtype = 1;
        } catch (EPrimeSyntaxException e1) {
            tokens.reset();
            try {
                tokens.mark();
                readTerminalString("exists");
                tokens.eraseMark();
                qtype = 2;
            } catch (EPrimeSyntaxException e2) {
                tokens.reset();
                readTerminalString("sum");
                qtype = 3;
            }
        }

        ASTNode idlist = readIdList();
        readTerminalString(":");
        ASTNode domain = readDomain();        // Should be some kind of bounded domain.
        readTerminalString(".");
        ASTNode qexpression = readExpression();
        if (VB_MTDS) {
            System.out.println("quantified expression read successfully");
        }

        // Wrap qexpression
        for (int i = idlist.numChildren() - 1; i >= 0; i--) {
            if (qtype == 1) {
                qexpression = new ForallExpression(idlist.getChild(i), domain, qexpression);
            } else if (qtype == 2) {
                qexpression = new ExistsExpression(idlist.getChild(i), domain, qexpression);
            } else {
                assert qtype == 3;
                qexpression = new QuantifiedSum(idlist.getChild(i), domain, qexpression);
            }
        }
        return qexpression;
    }

    // To be called directly after parsing an array expression of any kind.
    // Checks if the following token is "[", and if so, attempts to parse a
    // slice or deref. Recurses until there are no more slices/derefs.
    private ASTNode readSliceOrDeref(ASTNode arrayexp) throws EPrimeSyntaxException {
        readTerminalString("[");
        tokens.commit("Parsed '[' (start of matrix deref or slice)");

        ArrayList<ASTNode> r = readSliceRangeList();
        readTerminalString("]");

        boolean matrixslice = false;
        for (ASTNode a : r) {
            if (a.isSet()) {                // If any entry is not just a value but a set..
                matrixslice = true;
                break;
            }
        }

        ASTNode newex;
        if (matrixslice) {
            tokens.commit("Parsed ']' (end of matrix slice)");
            newex = new MatrixSlice(arrayexp, r, global_symbols);
        } else {
            tokens.commit("Parsed ']' (end of matrix deref)");
            newex = new MatrixDeref(arrayexp, r);
        }

        // Finally if this is a matrix slice, then need to recurse because it could
        // be followed by another matrix slice or deref.
        if (matrixslice) {
            try {
                tokens.mark("readslicerecurse");
                newex = readSliceOrDeref(newex);
                tokens.eraseMark("readslicerecurse");
            } catch (EPrimeSyntaxException e1) {
                tokens.reset("readslicerecurse");
            }
        }

        return newex;
    }

    /* =================================================================
    Either read an array function (at the moment just flatten) or an 
    array of some kind by calling readArrayExpressionInner,
    or a MatrixDeref because syntactically it is a MatrixSlice.
    ================================================================= */

    private ASTNode readArrayExpression() throws EPrimeSyntaxException {
        ASTNode arrayex;
        try {
            tokens.mark("arrayfunc");
            arrayex = readArrayFunction();
            tokens.eraseMark("arrayfunc");
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("arrayfunc");
            try {
                tokens.mark("arrayvarious");
                arrayex = readArrayExpressionInner();
                tokens.eraseMark("arrayvarious");
            }
            catch (EPrimeSyntaxException e2) {
                tokens.reset("arrayvarious");
                readTerminalString("(");
                arrayex=readArrayExpression();
                readTerminalString(")");
            }
        }
        
        // Optionally followed by slices/deref
        try {
            tokens.mark("slice");
            arrayex = readSliceOrDeref(arrayex);
            tokens.eraseMark("slice");
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("slice");
        }
        
        return arrayex;
    }

    /* ==================================================================
    Parse named functions that return arrays. 
    ===================================================================== */

    private ASTNode readArrayFunction() throws EPrimeSyntaxException {
        try {
            tokens.mark("flattn");
            readTerminalString("flatten");
            readTerminalString("(");
            tokens.commit("Read flatten(");
            try {
                tokens.mark("flattn2");
                ASTNode a = readArrayExpression();
                readTerminalString(")");
                tokens.eraseMark("flattn2");
                tokens.eraseMark("flattn");
                return new Flatten(a);
            }
            catch(EPrimeSyntaxException e) {
                tokens.reset("flattn2");
                int dim=readInt();
                if(dim<0) {
                    throw new EPrimeSyntaxException("Integer >=0 in flatten", String.valueOf(dim));
                }
                readTerminalString(",");
                ASTNode a = readArrayExpression();
                readTerminalString(")");
                
                tokens.eraseMark("flattn");
                
                while(dim>0) {
                    a=new ConcatenateMatrix(a);
                    dim--;
                }
                return a;
            }
        }
        catch(EPrimeSyntaxException e) {
            tokens.reset("flattn");
            return readFunctionGeneric();
        }
    }

    /* ====================================================================
     readArrayExpressionInner()
     Reads an individual id or matrix slice or construction 
     of a matrix from a list of expressions. 
    ==================================================================== */

    private ASTNode readArrayExpressionInner() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read array expression");
        }

        ASTNode arrayex;
        try {
            tokens.mark("comparray");
            arrayex = readCompoundOrComprehensionArrayExpression();
            tokens.eraseMark("comparray");
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("comparray");
            arrayex = readIdentifier();
        }

        if (VB_MTDS) {
            System.out.println("succeeded reading array expression: " + arrayex);
        }
        return arrayex;
    }

    /* ====================================================================
     readSliceRangeList()
     Either .. or an expression. 
    ==================================================================== */
    private ArrayList<ASTNode> readSliceRangeList() throws EPrimeSyntaxException {
        ArrayList<ASTNode> l = new ArrayList<ASTNode>();

        try {
            tokens.mark("readdotdot");
            readTerminalString("..");
            l.add(new IntegerDomain(new Range(null, null)));
            tokens.eraseMark("readdotdot");
            tokens.commit("Read '..' in matrix slice or deref.");
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("readdotdot");
            l.add(readExpression());            // Any value.
        }

        try {            // Optional suffix
            tokens.mark("rrl");
            readTerminalString(",");
            tokens.commit("Read ',' in matrix slice or deref.");
            l.addAll(readSliceRangeList());
            tokens.eraseMark("rrl");
        } catch (EPrimeSyntaxException e) {
            if (VB_MTDS) {
                System.out.println("is not a SliceRangeList");
            }
            tokens.reset("rrl");
        }
        return l;
        // if (VB_MTDS) System.out.println("RangeList read successfully") ;
    }

    private ASTNode readCompoundOrComprehensionArrayExpression() throws EPrimeSyntaxException {
        // This function used to simply branch for either compound or comprehension matrix.
        // However, this backtracks over the first expression inside the matrix:
        // "[" e1 "|"   or   "[" e1 ","
        // Now it behaves like the compound matrix parser until it hits the first case
        // above, and if it does so it jumps into the comprehension parser.

        readTerminalString("[");
        tokens.commit("Parsed '[' (start of matrix literal or matrix comprehension)");
        
        //  If it is an empty matrix, optionally read a matrix domain type. 
        try {
            tokens.mark("emptymatrix");
            readTerminalString("]");
            
            tokens.commit("Parsed '[ ]' (empty matrix)");
            
            try {
                tokens.mark("empty2");
                readTerminalString(":");
                tokens.commit("Parsed '[ ] :' (empty matrix with type annotation)");
                readTerminalString("`");
                tokens.commit("Parsed '[ ] : `' (empty matrix with type annotation)");
                ASTNode md=readMatrixDomain();
                readTerminalString("`");
                tokens.eraseMark("empty2");
                tokens.eraseMark("emptymatrix");
                return new EmptyMatrix(md);
            }
            catch (EPrimeSyntaxException e2) {
                tokens.reset("empty2");
            }
            
            tokens.eraseMark("emptymatrix");
            return CompoundMatrix.makeCompoundMatrix(new ArrayList<ASTNode>());
        }
        catch(EPrimeSyntaxException e1) {
            tokens.reset("emptymatrix");
        }
        
        boolean nocomma = false;

        ArrayList<ASTNode> l = new ArrayList<ASTNode>();
        while (true) {
            try {
                tokens.mark();
                ASTNode a = readExpression();
                tokens.eraseMark();
                l.add(a);
                if(l.size()==1) {
                    tokens.commit("Parsed first element in matrix literal or comprehension.");
                }
                else {
                    tokens.commit("Parsed element in matrix literal.");
                }
            } catch (EPrimeSyntaxException e2) {
                tokens.reset();
                break;
            }

            nocomma = ! tryReadTerminalString(",");
            if (nocomma) {
                break;
            }
            else {
                tokens.commit("Parsed ',' in matrix literal or comprehension.");
            }
        }

        if (nocomma && l.size() == 1) {
            // Parsed one expression and then failed to parse a comma.
            // Test for |
            boolean bar = tryReadTerminalString("|");
            if (bar) {
                tokens.commit("Parsed '|' (in comprehension)");
                // Jump into matrix comprehension parser.
                return readComprehensionInner(l.get(0));
            }
        }

        ASTNode dom = null;
        try {
            tokens.mark("optdom");
            readTerminalString(";");
            dom = readSimpleDomain();
            tokens.eraseMark("optdom");
        } catch (EPrimeSyntaxException e3) {
            tokens.reset("optdom");
        }

        readTerminalString("]");
        // Compound Matrices are indexed from 1 by default.
        if (dom == null) {
            return CompoundMatrix.makeCompoundMatrix(l);
        } else {
            return CompoundMatrix.makeCompoundMatrix(dom, l, false);
        }
    }

    private ASTNode readComprehensionInner(ASTNode innerexp) throws EPrimeSyntaxException {
        // readTerminalString("[");
        // ASTNode innerexp=readExpression();   // This could be almost anything. Should allow other matrix expressions inside.
        // readTerminalString("|");

        // "[" innerexp "|" have already been parsed.

        // Read quantifiers.
        ArrayList<ASTNode> quantifiers = new ArrayList<ASTNode>();
        boolean first = true;
        while (true) {
            try {
                tokens.mark("cquant");
                if (!first) {
                    readTerminalString(",");
                }

                ASTNode ids = readIdList();

                readTerminalString(":");

                ASTNode domain = readDomain();                // Should be some kind of bounded domain.

                tokens.eraseMark("cquant");
                for (int i =0; i < ids.numChildren(); i++) {
                    quantifiers.add(new ComprehensionForall(ids.getChild(i), domain));
                }
                first = false;
            } catch (EPrimeSyntaxException e2) {
                tokens.reset("cquant");
                break;
            }
        }

        // Now read conditions.
        ArrayList<ASTNode> conditions = new ArrayList<ASTNode>();
        while (true) {
            try {
                tokens.mark("ccond");
                if (!first) {
                    readTerminalString(",");
                }

                ASTNode condition = readExpression();

                tokens.eraseMark("ccond");
                conditions.add(condition);
                first = false;
            } catch (EPrimeSyntaxException e2) {
                tokens.reset("ccond");
                break;
            }
        }

        ASTNode dom = null;
        try {
            tokens.mark("optdom");
            readTerminalString(";");
            dom = readSimpleDomain();
            tokens.eraseMark("optdom");
        } catch (EPrimeSyntaxException e3) {
            tokens.reset("optdom");
        }

        readTerminalString("]");
        if (dom == null) {
            return new ComprehensionMatrix(innerexp, quantifiers, new And(conditions));
        } else {
            return new ComprehensionMatrix(innerexp, quantifiers, new And(conditions), dom);
        }
    }

    ////////////////////////////////////////////////////////////////////////////// 
    // 
    // Optimisation -- reading large matrices of numbers typically in a letting.
    // Fails if it is anything other than a matrix of numbers.
    // Also fails if the matrix is followed by a "[" because this case should
    // be read somewhere else.

    private ASTNode readQuickConstantMatrixNotDeref() throws EPrimeSyntaxException {

        ASTNode m = readQuickConstantMatrix();

        // check there is no "[" following.
        tokens.mark("brac");
        boolean flag = false;
        try {
            readTerminalString("[");
            flag = true;
        } catch (EPrimeSyntaxException e) { }

        tokens.reset("brac");

        if (flag) {
            throw new EPrimeSyntaxException("quick matrix", "matrix deref");
        } else {
            return m;
        }
    }

    private ASTNode readQuickConstantMatrix() throws EPrimeSyntaxException {
        // read some number of [
        readTerminalString("[");

        ArrayList<ASTNode> l = new ArrayList<ASTNode>();
        while (true) {

            // read a nested matrix or an integer.
            try {
                tokens.mark();
                int a = readInt();
                tokens.eraseMark();
                l.add(new NumberConstant(a));

            } catch (EPrimeSyntaxException e2) {
                tokens.reset();
                try {
                    tokens.mark("nested");
                    ASTNode a = readQuickConstantMatrix();
                    tokens.eraseMark("nested");
                    l.add(a);
                } catch (EPrimeSyntaxException e3) {
                    tokens.reset("nested");
                    break;
                }
            }

            // read a comma
            try {
                tokens.mark();
                readTerminalString(",");
                tokens.eraseMark();
            } catch (EPrimeSyntaxException e2) {
                tokens.reset();
                break;
            }
        }

        readTerminalString("]");
        // Compound Matrices are indexed from 1 by default.
        return CompoundMatrix.makeCompoundMatrix(l);
    }

    // Ranges %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    /* ====================================================================
     readRangeList()
     <RangeList> ::= <OpenRange> ["," <RangeList>]
    ==================================================================== */
    private ArrayList<ASTNode> readRangeList() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read RangeList");
        }
        ASTNode a = readOpenRange();
        ArrayList<ASTNode> l = new ArrayList<ASTNode>();
        l.add(a);
        try {            // Optional suffix
            tokens.mark("rrl");
            readTerminalString(",");
            l.addAll(readRangeList());
            tokens.eraseMark("rrl");
        } catch (EPrimeSyntaxException e) {
            if (VB_MTDS) {
                System.out.println("is not a RangeList");
            }
            tokens.reset("rrl");
        }
        return l;
        // if (VB_MTDS) System.out.println("RangeList read successfully") ;
    }

    /* ====================================================================
	 readOpenRange()
	 <OpenRange>    ::= <ClosedRange> |  ".." | <ArithExpr> ".." | ".."  
	 <ArithExpr>
    ==================================================================== */
    private ASTNode readOpenRange() throws EPrimeSyntaxException {
        boolean startdots = tryReadTerminalString("..");

        if (startdots) {
            try {
                tokens.mark("oprange");
                ASTNode right = readExpression();
                tokens.eraseMark("oprange");
                return new Range(null, right);
            } catch (EPrimeSyntaxException e1) {
                tokens.reset("oprange");
                return new Range(null, null);
            }
        } else {
            ASTNode left = readExpression();

            boolean middots = tryReadTerminalString("..");
            if (middots) {
                try {
                    tokens.mark("oprange2");
                    ASTNode right = readExpression();
                    tokens.eraseMark("oprange2");
                    return new Range(left, right);
                } catch (EPrimeSyntaxException e1) {
                    tokens.reset("oprange2");
                    return new Range(left, null);
                }
            } else {
                return left;
            }
        }
    }

    // Ops %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


    /* ====================================================================
     readBinOp()
     <BinOp> ::= + - / * ** %       
     \/  /\  =>  <=>        ->  <->
     = != <= < >= > <lex <=lex >lex >=lex in
     union intersect  (set operators, as well as -)
    ==================================================================== */

    private ASTNode readBinOp() throws EPrimeSyntaxException {
        tokens.nextToken();
        if (tokens.tokenType != EPrimeTokenizer.TT_OTHER) {
            if (tokens.tokenType != EPrimeTokenizer.TT_WORD || !binops.contains(tokens.wordToken)) {
                throw new EPrimeSyntaxException("Binary operator", tokens.toString());
            }
        }

        String tok;
        if (tokens.tokenType == EPrimeTokenizer.TT_OTHER) {
            tok = tokens.otherToken;
        } else {
            tok = tokens.wordToken;
        }

        if (!binops.contains(tok)) {
            throw new EPrimeSyntaxException("Binary operator", tok);
        }
        return new BinOpPlaceholder(tok);
    }

    // Identifiers %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    /* ====================================================================
     readIdList()
     <IdList> ::= Identifier ["," <IdList>]
    ==================================================================== */
    private ASTNode readIdList() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read IdList");
        }
        ArrayList<ASTNode> theList = new ArrayList<ASTNode>();
        theList.add(readIdentifier());
        while (true) {
            try {
                tokens.mark("readidlist");
                readTerminalString(",");
                theList.add(readIdentifier());
                tokens.eraseMark("readidlist");
            } catch (EPrimeSyntaxException e) {
                tokens.reset("readidlist");
                break;
            }
        }        // end of while loop
        if (VB_MTDS) {
            System.out.println("IdList read successfully");
        }
        return new Container(theList);
    }

    /* ====================================================================
     readTuple()
     read a tuple of constants like <1,2,3>. Old syntax, only used in table constraint. 
    ==================================================================== */
    private ASTNode readTuple() throws EPrimeSyntaxException {
        readTerminalString("<");
        ArrayList<ASTNode> tupleContents = new ArrayList<ASTNode>();
        while (true) {
            try {
                tokens.mark();
                tupleContents.add(readConstant());
                tokens.eraseMark();
            } catch (EPrimeSyntaxException e1) {
                tokens.reset();
                break;
            }
            try {
                tokens.mark();
                readTerminalString(",");
                tokens.eraseMark();
            } catch (EPrimeSyntaxException e1) {
                tokens.reset();
                break;
            }
        }        // end of while loop

        readTerminalString(">");
        return CompoundMatrix.makeCompoundMatrix(tupleContents);
    }

    /* ====================================================================
     readIdentifier()
     Rejects identifiers with leading digits (because read as a number).
     Should also reject reserved words like matrix. 
    ==================================================================== */

    private ASTNode readIdentifier() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read Identifier");
        }
        tokens.nextToken();
        if (tokens.tokenType != EPrimeTokenizer.TT_WORD) {
            throw new EPrimeSyntaxException("an identifier string", tokens.toString());
        }
        if (keywords.contains(tokens.wordToken)) {
            throw new EPrimeSyntaxException("an identifier string", tokens.toString());
        }

        if (VB_MTDS) {
            System.out.println("succeeded to read Identifier " + tokens.wordToken);
        }
        return new Identifier(tokens.wordToken, global_symbols);
    }

    // Join some ranges into a finite domain. Make a shallow-ish tree.
    private ASTNode makeUnionOfRanges(ArrayList<ASTNode> ranges, int start, int end) {
        if (end - start < 5) {
            ASTNode union = new IntegerDomain(ranges.get(start));
            for (int i = start + 1; i < end; i++) {
                union = new Union(union, new IntegerDomain(ranges.get(i)));
            }
            return union;
        } else {
            return new Union(makeUnionOfRanges(ranges, start, start + (end - start) / 2), makeUnionOfRanges(ranges, start + (end - start) / 2, end));
        }
    }
    
    /* ====================================================================
     readUnboundedSetOfRanges()
     <UnboundedSetOfRanges> ::=
     (<Expression> ".." <Expression> | <Expression>) ["," <UnboundedSetOfRanges>]
    ==================================================================== */

    private ASTNode readUnboundedSetOfRanges() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read UnboundedSetOfRanges");
        }
        ArrayList<ASTNode> ranges = readRangeList();

        if (VB_MTDS) {
            System.out.println("UnboundedSetOfRanges read successfully");
        }

        return makeUnionOfRanges(ranges, 0, ranges.size());
    }

    // Terminals %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    /* ====================================================================
     readConstant()
	 Either an Integer, or "true", or "false"
    ==================================================================== */
    private ASTNode readConstant() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("attempting to read constant");
        }
        try {
            tokens.mark();
            int a = readInt();
            tokens.eraseMark();
            return new NumberConstant(a);
        } catch (EPrimeSyntaxException e1) {
            tokens.reset();
            try {
                tokens.mark();
                readTerminalString("true");
                tokens.eraseMark();
                return new BooleanConstant(true);
            } catch (EPrimeSyntaxException e2) {
                tokens.reset();
                readTerminalString("false");
                return new BooleanConstant(false);
            }
        }
        // if (VB_MTDS) System.out.println("constant read successfully") ;
    }

    /* ====================================================================
     readInt()
     Should not accept -5 -- this should be read as unaryminus 5. 
    ==================================================================== */

    private int readInt() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("Attempting to read Int");
        }
        tokens.nextToken();
        if (tokens.tokenType == EPrimeTokenizer.TT_INT) {
            return tokens.intToken;
        }

        throw new EPrimeSyntaxException("integer", tokens.toString());
    }

    /* ====================================================================
     readTerminalString()
    ==================================================================== */

    private boolean tryReadTerminalString(String t) {
        try {
            tokens.mark("trts");
            readTerminalString(t);
            tokens.eraseMark("trts");
            return true;
        } catch (EPrimeSyntaxException e1) {
            tokens.reset("trts");
            return false;
        }
    }

    private void readTerminalString(String terminal) throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("Attempting to read Terminal: " + terminal);
        }
        tokens.nextToken();
        if ((tokens.tokenType != EPrimeTokenizer.TT_WORD) && (tokens.tokenType != EPrimeTokenizer.TT_OTHER)) {
            throw new EPrimeSyntaxException(terminal, tokens.toString());
        }
        if ((tokens.tokenType == EPrimeTokenizer.TT_WORD) && (!tokens.wordToken.equals(terminal))) {
            throw new EPrimeSyntaxException(terminal, tokens.toString());
        }
        if ((tokens.tokenType == EPrimeTokenizer.TT_OTHER) && (!tokens.otherToken.equals(terminal))) {
            throw new EPrimeSyntaxException(terminal, tokens.toString());
        }
        if (VB_MTDS) {
            System.out.println("Terminal read successfully");
        }
    }

    private String readTerminalString() throws EPrimeSyntaxException {
        if (VB_MTDS) {
            System.out.println("Attempting to read terminal string.");
        }
        tokens.nextToken();
        if ((tokens.tokenType == EPrimeTokenizer.TT_WORD)) {
            return tokens.wordToken;
        }
        if ((tokens.tokenType == EPrimeTokenizer.TT_OTHER)) {
            return tokens.otherToken;
        }
        throw new EPrimeSyntaxException("", tokens.toString());
    }
    
    
    public class FunctionDescription {
        
        FunctionDescription(String nam, int arg) {
            className=nam;
            numArgs=arg;
        }
        
        public String className;
        public int numArgs;
        
        
        public ASTNode construct(ASTNode slot1, ASTNode slot2, ASTNode slot3) {
            try {
                Class<?> t = Class.forName("savilerow.expression."+className);
                Class<?> astnode=Class.forName("savilerow.expression.ASTNode");
                
                if(numArgs==1) {
                    Constructor<?> c=t.getConstructor(astnode);
                    return (ASTNode) c.newInstance(slot1);
                }
                else if(numArgs==2) {
                    Constructor<?> c=t.getConstructor(astnode, astnode);
                    return (ASTNode) c.newInstance(slot1, slot2);
                }
                else if(numArgs==3) {
                    Constructor<?> c=t.getConstructor(astnode, astnode, astnode);
                    return (ASTNode) c.newInstance(slot1, slot2, slot3);
                }
            }
            catch (Exception e) {
                CmdFlags.errorExit("Failed to construct function "+className+" with exception "+e);
            }
            
            return null;
        }
    }
}