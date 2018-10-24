/*
* Minion http://minion.sourceforge.net
* Copyright (C) 2006-09
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

/** @help input Description

Minion expects to be provided with the name of an input file as an
argument. This file contains a specification of the CSP to be solved
as well as settings that the search process should use. The format is

Minion3Input::= MINION 3
                <InputSection>+
                **EOF**

InputSection::= <VariablesSection>
              | <SearchSection>
              | <ConstraintsSection>
              | <TuplelistSection>
              | <ShortTuplelistSection>

i.e. 'MINION 3' followed by any number of variable, search,
constraints and tuplelists sections (can repeat) followed by
'**EOF**', the end of file marker.

All text from a '#' character to the end of the line is ignored.

See the associated help entries below for information on each section.
*/

/** @help input Notes
You can give an input file via standard input by specifying '--' as the file
name, this might help when minion is being used as a tool in a shell script or
for compressed input, e.g.,

   gunzip -c myinput.minion.gz | minion
*/

/** @help input;variables Description
The variables section consists of any number of variable declarations
on separate lines.

VariablesSection::= **VARIABLES**
                    <VarDeclaration>*
*/

/** @help input;variables Example
 **VARIABLES**

BOOL bool                          #boolean var
BOUND b {1..3}                     #bounds var
SPARSEBOUND myvar {1,3,4,6,7,9,11} #sparse bounds var
DISCRETE d[3] {1..3}               #array of discrete vars
*/

/** @help input;variables References
See the help section

   help variables

for detailed information on variable declarations.
*/

/** @help input;constraints Description

The constraints section consists of any number of constraint
declarations on separate lines.

ConstraintsSection::= **CONSTRAINTS**
                      <ConstraintDeclaration>*
*/

/** @help input;constraints Example
**CONSTRAINTS**
eq(bool,0)
alldiff(d)
*/

/** @help input;constraints References
See help entries for individual constraints under

   help constraints

for details on constraint declarations.
*/

/** @help input;tuplelist Description
A tuplelist section lists of allowed tuples for table constraints
can be specified. This technique is preferable to specifying the
tuples in the constraint declaration, since the tuplelists can be
shared between constraints and named for readability.

The required format is

TuplelistSection::= **TUPLELIST**
                    <Tuplelist>*

Tuplelist::= <name> <num_tuples> <tuple_length> <numbers>+
*/

/** @help input;tuplelist Example
**TUPLELIST**
AtMostOne 4 3
0 0 0
0 0 1
0 1 0
1 0 0
*/

/** @help input;tuplelist References
help constraints table
help input shorttuplelist
*/

/** @help input;shorttuplelist Description
A shorttuplelist section lists of allowed tuples for haggisgac
and other constraints which accept short tuple lists.

The required format is

TuplelistSection::= **TUPLELIST**
                    <Tuplelist>*

Tuplelist::= <name> <num_tuples> <short_tuple>+

short_tuple ::= [ <literal>*, ]

literal ::= (<num>, <num>)
*/

/** @help input;shorttuplelist Example
**SHORTTUPLELIST**
mycon 4
[(0,0),(3,0)]
[(1,0),(3,0)]
[(2,0),(3,0)]
[(0,1),(1,1),(2,1),(3,1)]

Represents the same constraint as:

**TUPLELIST**
mycon 8 4
0 0 0 0
0 0 1 0
0 1 0 0
0 1 1 0
1 0 0 0
1 0 1 0
1 1 0 0
1 1 1 1

Short tuples give us a way of shrinking this list. Short tuples consist
of pairs (x,y), where x is a varible position, and y is a value for that
variable. For example:

[(0,0),(3,0)]

Represents 'If the variable at index 0 is 0, and the variable at index
3 is 0, then the constraint is true'.


Note that some tuples are double-represented in the example 'mycon'.
The first 3 short tuples all allow the assignment '0 0 0 0'. This is fine.
The important thing for efficency is to try to give a small list of
short tuples.
*/

/** @help input;shorttuplelist References
help constraints haggisgac
help constraints haggisgac-stable
help input tuplelist
*/



/** @help input;search Description

Inside the search section one can specify

- variable orderings,
- value orderings,
- optimisation function, and
- details of how to print out solutions.

   SearchSection::= <VarValOrdering>*
                    <OptimisationFn>?
                    <PrintFormat>?

If no varval ordering is given then the variables are assigned in instantiation
order and the values tried in ascending order.

If a variable order is given as a command line argument it will override
anything specified in the input file.

Multiple variable orders can be given, each with an optional value ordering:

   VarValOrdering::= <VarOrder>
                     <ValOrder>?

In each VarOrder an instantiation order is specified for a subset of
variables. Variables can optionally be \"auxiliary variables\" (add \"AUX\" to
the varorder) meaning that if there are several solutions to the problem
differing only in the auxiliary variables, only one is reported by minion.

   VarOrder::= VARORDER AUX? <ORDER>? [ <varname>+ ]

      where

   <ORDER>::= STATIC | SDF | SRF | LDF | ORIGINAL | WDEG | CONFLICT | DOMOVERWDEG

The value ordering allows the user to specify an instantiation order
for the variables involved in the variable order, either ascending (a)
or descending (d) for each. When no value ordering is specified, the
default is to use ascending order for every search variable.

   ValOrder::= VALORDER[ (a|d)+ ]

To model an optimisation problem the user can specify to minimise
or maximise a variable's value.

   OptimisationFn::= MAXIMISING <varname>
                   | MINIMISING <varname>

Finally, the user can control some aspects of the way solutions are
printed. By default (no PrintFormat specified) all the variables are
printed in declaration order. Alternatively a custom vector, or ALL
variables, or no (NONE) variables can be printed. If a matrix or, more
generally, a tensor is given instead of a vector, it is automatically
flattened into a vector as described in 'help variables vectors'.

   PrintFormat::= PRINT <vector>
                | PRINT ALL
                | PRINT NONE
*/

/** @help input;search References
See also

   switches -varorder
*/

/** @help input;example Example
Below is a complete minion input file with commentary, as an example.

MINION 3

# While the variable section doesn't have to come first, you can't
# really do anything until
# You have one...
**VARIABLES**

# There are 4 type of variables
BOOL bool         # Boolean don't need a domain
BOUND b {1..3}    # Bound vars need a domain given as a range
DISCRETE d {1..3} # So do discrete vars

#Note: Names are case sensitive!

# Internally, Bound variables are stored only as a lower and upper bound
# Whereas discrete variables allow any sub-domain

SPARSEBOUND s {1,3,6,7} # Sparse bound variables take a sorted list of values

# We can also declare matrices of variables!

DISCRETE q[3] {0..5} # This is a matrix with 3 variables: q[0],q[1] and q[2]
BOOL bm[2,2] # A 2d matrix, variables bm[0,0], bm[0,1], bm[1,0], bm[1,1]
BOOL bn[2,2,2,2] # You can have as many indices as you like!

#The search section is entirely optional
**SEARCH**

# Note that everything in SEARCH is optional, and can only be given at
# most once!

# If you don't give an explicit variable ordering, one is generated.
# These can take matrices in interesting ways like constraints, see below.
VARORDER [bool,b,d]

# If you don't give a value ordering, 'ascending' is used
#VALORDER [a,a,a,a]

# You can have one objective function, or none at all.
MAXIMISING bool
# MINIMISING x3

# both (MAX/MIN)IMISING and (MAX/MIN)IMIZING are accepted...


# Print statement takes a vector of things to print

PRINT [bool, q]

# You can also give:
# PRINT ALL (the default)
# PRINT NONE


# Declare constraints in this section!
**CONSTRAINTS**

# Constraints are defined in exactly the same way as in MINION input
formats 1 & 2
eq(bool, 0)
eq(b,d)

# To get a single variable from a matrix, just index it
eq(q[1],0)
eq(bn[0,1,1,1], bm[1,1])

# It's easy to get a row or column from a matrix. Just use _ in the
# indices you want
# to vary. Just giving a matrix gives all the variables in that matrix.

#The following shows how flattening occurs...

# [bm] == [ bm[_,_] ] == [ bm[0,0], bm[0,1], bm[1,0], bm[1,1] ]
# [ bm[_,1] ] = [ bm[0,1], bm[1,1] ]
# [ bn[1,_,0,_] = [ bn[1,0,0,0], b[1,0,0,1], b[1,1,0,0], b[1,1,0,1] ]

# You can string together a list of such expressions!

lexleq( [bn[1,_,0,_], bool, q[0]] , [b, bm, d] )

# One minor problem.. you must always put [ ] around any matrix expression, so
# lexleq(bm, bm) is invalid

lexleq( [bm], [bm] ) # This is OK!

# Can give tuplelists, which can have names!
# The input is: <name> <num_of_tuples> <tuple_length> <numbers...>
# The formatting can be about anything..

**TUPLELIST**

Fred 3 3
0 2 3
2 0 3
3 1 3

Bob 2 2 1 2 3 4

#No need to put everything in one section! All sections can be reopened..
**VARIABLES**

# You can even have empty sections.. if you want

**CONSTRAINTS**

#Specify tables by their names..

table([q], Fred)

# Can still list tuples explicitally in the constraint if you want at
# the moment.
# On the other hand, I might remove this altogether, as it's worse than giving
# Tuplelists

table([q],{ <0,2,3>,<2,0,3>,<3,1,3> })

#Must end with the **EOF** marker!

**EOF**

Any text down here is ignored, so you can write whatever you like (or
nothing at all...)
*/


// Plan here is to generate an instance of a problem (or whatever you have)
// and return that.


#include <string>

#include "MinionInputReader.h"

#define MAYBE_PARSER_INFO(X) { if(this->parser_verbose) { this->parser_info(X); } }

template<typename FileReader>
void MinionThreeInputReader<FileReader>::parser_info(string s)
{
  if(parser_verbose)
    cout << s << endl;
}

//This function is called to finalise reading an instance that may have
//consisted of two input files. If the first input file was a "MINION 1" then
//this function should be safe for it also.
template<typename FileReader>
void MinionThreeInputReader<FileReader>::finalise() {
  if(isGadgetReader() && instance->constructionSite.empty())
    throw parse_exception("Gadgets need a construction site!");

  // Fill in any missing defaults
  if(instance->search_order.empty())
  {
    MAYBE_PARSER_INFO("No order generated, auto-generating complete order");
    instance->search_order.push_back(instance->vars.get_all_vars());
  }

  vector<Var> all_vars = instance->vars.get_all_vars();
  set<Var> unused_vars(all_vars.begin(), all_vars.end());
  for(SysInt i = 0; i < (SysInt)instance->search_order.size(); ++i)
  {
    const vector<Var>& vars_ref = instance->search_order[i].var_order;
    for(vector<Var>::const_iterator it = vars_ref.begin(); it != vars_ref.end(); ++it)
    {
      unused_vars.erase(*it);
    }
  }

  if(!unused_vars.empty() && ensure_branch_on_all_vars)
  {
    vector<Var> unused_vec(unused_vars.begin(), unused_vars.end());
    if(instance->search_order.size() > 1 && instance->search_order.back().find_one_assignment == true)
    {
      instance->search_order.back().var_order.insert(
        instance->search_order.back().var_order.end(), unused_vec.begin(), unused_vec.end());
    }
    else
    {
      instance->search_order.push_back(unused_vec);
      instance->search_order.back().find_one_assignment=true;
    }
  }

  for(SysInt i = 0; i < (SysInt)instance->search_order.size(); ++i)
    instance->search_order[i].setupValueOrder();

  // This has to be delayed unless not all variables are defined where 'PRINT ALL' occurs.
  if(print_all_vars)
    instance->print_matrix = instance->all_vars_list;

  if(instance->sym_order.empty())
    instance->sym_order = instance->vars.get_all_vars();

  if(instance->sym_order.size() != instance->vars.get_all_vars().size())
  {
    MAYBE_PARSER_INFO("Extending symmetry order with auxillery variables");
    vector<Var> all_vars = instance->vars.get_all_vars();
    for(typename vector<Var>::iterator i = all_vars.begin(); i != all_vars.end(); ++i)
    {
      if(find(instance->sym_order.begin(), instance->sym_order.end(), *i) == instance->sym_order.end() )
        instance->sym_order.push_back(*i);
    }
  }

  if(instance->sym_order.size() != set<Var>(instance->sym_order.begin(), instance->sym_order.end()).size())
     throw parse_exception("SYMORDER cannot contain any variable more than once");

   if(instance->sym_order.size() != instance->vars.get_all_vars().size())
     throw parse_exception("SYMORDER must contain every variable");
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// read
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionThreeInputReader<FileReader>::read(FileReader* infile) {
  string s = infile->get_asciistring();
  MAYBE_PARSER_INFO("Read: '" + s + "'");

  string eof, wrong_eof;

  if(isGadgetReader())
  {
    eof = "**GADGET_END**";
    wrong_eof = "**EOF**";
  }
  else
  {
    eof = "**EOF**";
    wrong_eof = "**GADGET_END**";
  }

  while(s != eof)
  {
    if(s == "**VARIABLES**")
      readVars(infile);
    else if(s == "**SEARCH**")
      readSearch(infile);
    else if(s == "**TUPLELIST**")
      readTuples(infile);
    else if(s == "**SHORTTUPLELIST**")
      readShortTuples(infile);
    else if(s =="**CONSTRAINTS**")
    {
      while(infile->peek_char() != '*')
        instance->constraints.push_back(readConstraint(infile, false));
    }
    else if(s == "**GADGET**")
      { readGadget(infile); }
    else if(s == wrong_eof)
    {
      throw parse_exception("Section terminated with " + wrong_eof +
        " instead of " + eof);
    }
    else
      throw parse_exception("Don't understand '" + s + "' as a section header");
    s = infile->get_asciistring();
    MAYBE_PARSER_INFO("Read: '" + s + "'");
  }

  MAYBE_PARSER_INFO("Reached end of CSP");
}

template<typename FileReader>
void MinionThreeInputReader<FileReader>::readGadget(FileReader* infile)
{
  MAYBE_PARSER_INFO("Entering gadget parsing");
  if(isGadgetReader())
    throw parse_exception("Gadgets can't have gadgets!");

  infile->check_string("NAME");
  string name = infile->get_string();
  MAYBE_PARSER_INFO("Gadget name:" + name);

  MinionThreeInputReader gadget(parser_verbose, map_long_short_mode, ensure_branch_on_all_vars);
  CSPInstance* new_instance = new CSPInstance;
  gadget.instance = new_instance;
  gadget.setGadgetReader();
  gadget.read(infile);

  // Take the CSPInstance out of the Minion3InputReader, and make a copy of it.
  instance->addGadgetSymbol(name, shared_ptr<CSPInstance>(new_instance));
  MAYBE_PARSER_INFO("Exiting gadget parsing");
}


//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readConstraint
// Recognise constraint by its name, read past name and leading '('
// Return false if eof or unknown ct. Else true.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
ConstraintBlob MinionThreeInputReader<FileReader>::readConstraint(FileReader* infile, BOOL reified) {
  string id = infile->getline('(');

  SysInt constraint_num = -1;
  for(SysInt i = 0; i < num_of_constraints; ++i)
  {
    if(constraint_list[i].name == id)
    {
      constraint_num = i;
      break;
    }
  }

  if(constraint_num == -1)
  {
    if (infile->eof())
    {
      throw parse_exception(string("Bad Constraint Name or reached end of file: '") + id + "'");
    }
    else
      { throw parse_exception(string("Unknown Constraint: '") + id + string("'")); }
  }
  ConstraintDef* constraint = constraint_list + constraint_num;

  switch(constraint->type)
  {
#ifdef CT_WATCHED_OR_ABC
    case CT_WATCHED_OR:
    return readConstraintOr(infile, get_constraint(CT_WATCHED_OR));
    break;
#endif

#ifdef CT_GADGET_ABC
    case CT_GADGET:
    return readConstraintGadget(infile);
    break;
#endif

    default:
    if(constraint->number_of_params == 2 &&
         (constraint->read_types[1] == read_tuples || constraint->read_types[1] == read_short_tuples) )
      return readConstraintTable(infile, constraint);
    else
      return readGeneralConstraint(infile, constraint);
  }
  // g++ seems to think compilation can get here. I disagree, but putting a catch doesn't hurt.
  throw parse_exception("Fatal error in parsing constraints");
}

template<typename FileReader>
ConstraintBlob MinionThreeInputReader<FileReader>::readConstraintTable(FileReader* infile, ConstraintDef* def)
{
  ConstraintBlob con(def);

  con.vars.push_back(readLiteralVector(infile));
  infile->check_sym(',');

  if(def->read_types[1] == read_tuples)
    con.tuples = readConstraintTupleList(infile);
  else if(def->read_types[1] == read_short_tuples)
    con.short_tuples = readConstraintShortTupleList(infile);
  else
    assert(0);

  infile->check_sym(')');

  if(def->read_types[1] == read_tuples)
  {
    if((SysInt)con.vars[0].size() != con.tuples->tuple_size())
    {
      throw parse_exception("Tuple constraint with " + tostring(con.vars[0].size()) +
                            " variables cannot have tuples of length " + tostring(con.tuples->tuple_size()));
    }
  }


  if(con.vars[0].size() == 0)
  {
    if(def->read_types[1] == read_tuples)
    {
      // Either trivially true, or trivially false, depending on how many tuples there are.
      if(con.tuples->size() != 0)
        return ConstraintBlob(get_constraint(CT_TRUE));
      else
        return ConstraintBlob(get_constraint(CT_FALSE));
    }
    else
    {
      if(con.short_tuples->size() == 0)
        return ConstraintBlob(get_constraint(CT_FALSE));
      else if((*con.short_tuples->tuplePtr())[0].empty())
          return ConstraintBlob(get_constraint(CT_TRUE));
      else throw parse_exception("Not a valid list of short tuples for a constraint with no variables!");
    }
  }

  return con;
}

template<typename FileReader>
ConstraintBlob MinionThreeInputReader<FileReader>::readGeneralConstraint(FileReader* infile, ConstraintDef* def)
{
  // This slightly strange code is to save copying the ConstraintBlob as much as possible.
  ConstraintBlob con(def);
  vector<vector<Var> >& varsblob = con.vars;
  vector<vector<DomainInt> >& constblob = con.constants;

  for(SysInt i = 0; i < def->number_of_params; ++i)
  {
    switch(def->read_types[i])
    {
      case read_list:
      varsblob.push_back(readLiteralVector(infile));
      break;
      case read_var:
      varsblob.push_back(make_vec(readIdentifier(infile)));
      break;
      case read_2_vars:
      {
        vector<Var> vars(2);
        vars[0] = readIdentifier(infile);
        infile->check_sym(',');
        vars[1] = readIdentifier(infile);
        varsblob.push_back(std::move(vars));
      }
      break;
      case read_constant:
      constblob.push_back(make_vec(infile->read_num()));
      break;
      case read_constant_list:
      {
        vector<Var> vectorOfConst = readLiteralVector(infile);
        vector<DomainInt> vals;
          for(UnsignedSysInt loop = 0; loop < vectorOfConst.size(); ++loop)
          {
            if(vectorOfConst[loop].type() != VAR_CONSTANT)
                throw parse_exception("Vector must only contain constants.");
              else
          vals.push_back(vectorOfConst[loop].pos());
          }
          constblob.push_back(std::move(vals));
      }
      break;
      case read_constraint:
      con.internal_constraints.push_back(readConstraint(infile, false));
      break;
      case read_constraint_list:
      con.internal_constraints = readConstraintList(infile);
      break;
      case read_tuples:
      if(con.tuples == NULL)
        con.tuples = readConstraintTupleList(infile);
      else if(con.tuples2 == NULL)
        con.tuples2 = readConstraintTupleList(infile);
      else throw parse_exception("Too many tuplelists");
      break;
      default:
      D_FATAL_ERROR("Internal Error!");
    }
    if(i != def->number_of_params - 1)
      infile->check_sym(',');
  }
  infile->check_sym(')');

  return con;
}

template<typename FileReader>
ShortTupleList* MinionThreeInputReader<FileReader>::readConstraintShortTupleList(FileReader* infile)
{

  string name = infile->get_string();
  return instance->getShortTableSymbol(name);
}

template<typename FileReader>
TupleList* MinionThreeInputReader<FileReader>::readConstraintTupleList(FileReader* infile)
{
  TupleList* tuplelist;

  if(infile->peek_char() != '{')
  {
    string name = infile->get_string();
    tuplelist = instance->getTableSymbol(name);
  }
  else
  {
    vector<vector<DomainInt> > tuples ;
    infile->check_sym('{');
    char delim = infile->peek_char();

    SysInt tupleSize = 0;

    while (delim != '}')
    {
      infile->check_sym('<');
      vector<DomainInt> tuple;
      // Optimisation
      tuple.reserve(tupleSize);

      char next_char = ',';
      while(next_char == ',')
      {
        tuple.push_back(infile->read_num());
        next_char = infile->get_char();
      }
      if(next_char != '>')
        throw parse_exception("Expected ',' or '>'");

      if(tupleSize == 0)
        tupleSize = tuple.size();
      if(tupleSize != (SysInt)tuple.size())
        throw parse_exception("All tuples in each constraint must be the same size!");
      tuples.push_back(std::move(tuple)) ;

      delim = infile->get_char();                          // ',' or '}'
      if(delim != ',' && delim!= '}')
        throw parse_exception("Expected ',' or '}'");
    }
    tuplelist = instance->tupleListContainer->getNewTupleList(tuples);
    instance->addUnnamedTableSymbol(tuplelist);
  }

  return tuplelist;
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readConstraintGadget
// table(<vectorOfVars>, {<tuple> [, <tuple>]})
// Tuples represented as a vector of SysInt arrays.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
#ifdef CT_GADGET_ABC
template<typename FileReader>
ConstraintBlob MinionThreeInputReader<FileReader>::readConstraintGadget(FileReader* infile)
{
  MAYBE_PARSER_INFO( "Reading a gadget constraint" ) ;

  vector<Var> vectorOfVars = readLiteralVector(infile) ;

  infile->check_sym(',');

  string s = infile->get_string();

  MAYBE_PARSER_INFO( "Gadget name: '" + s + "'");
  shared_ptr<CSPInstance> in_gadget = instance->getGadgetSymbol(s);
  ConstraintBlob gadgetCon( get_constraint(CT_GADGET) , vectorOfVars);
  gadgetCon.gadget = in_gadget;
  infile->check_sym(',');
  gadgetCon.gadget_prop_type = GetPropMethodFromString(infile->get_string());
  infile->check_sym(')');
  MAYBE_PARSER_INFO("End gadget reading");
  return gadgetCon;
}
#endif

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readConstraintOr
// or(<vectorOfVars>)
// SAT clauses represented as literals and negated literals
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
ConstraintBlob MinionThreeInputReader<FileReader>::readConstraintOr(FileReader* infile,
  ConstraintDef* ct)
{
  MAYBE_PARSER_INFO("Reading a SAT clause");
  infile->check_sym('[');
  vector<DomainInt> negs;
  vector<Var> clause_vars;
  if(infile->peek_char() != ']')
    clause_vars.push_back(readIdentifier(infile));
  while(infile->peek_char() != ']') {
    infile->check_sym(',');
    clause_vars.push_back(readIdentifier(infile));
  }
  infile->check_sym(']');
  infile->check_sym(')');
  for(SysInt i = 0; i < (SysInt)clause_vars.size(); i++) {
    if(clause_vars[i].type() == VAR_NOTBOOL) {
      negs.push_back(0);
      clause_vars[i].setType(VAR_BOOL);
    } else {
      negs.push_back(1);
    }
  }
  ConstraintBlob cb(ct, clause_vars);
  cb.negs = negs;
  return cb;
}

/// Reads an identifier which represents a single variable or constant.
template<typename FileReader>
Var MinionThreeInputReader<FileReader>::readIdentifier(FileReader* infile) {
  char idChar = infile->peek_char();

  if ((('0' <= idChar) && ('9' >= idChar)) || idChar == '-') {
    DomainInt i = infile->read_num();
    return Var(VAR_CONSTANT, i);
  }
  bool negVar = false;
  // Check to see if this is a negated Boolean
  if(infile->peek_char() == '!')
  {
    negVar = true;
    infile->get_char();
  }

  string name = infile->get_string();
  Var var = instance->vars.getSymbol(name);
  if(var.type() == VAR_MATRIX)
  {
    vector<DomainInt> params = readConstantVector(infile);
    vector<DomainInt> max_index = instance->vars.getMatrixSymbol(name);
    if(params.size() != max_index.size())
      throw parse_exception("Can't index a " + tostring(max_index.size()) +
      "-d matrix with " + tostring(params.size()) +
      " indices.");
    for(SysInt i = 0; i < (SysInt)params.size(); ++i)
    {
      if(params[i] < 0 || params[i] >= max_index[i])
        throw parse_exception(tostring(i) + string("th index is out of bounds,") +
        tostring(params[i]) + " is not between 0 and " +
        tostring(max_index[i] - 1));
    }
    name += to_var_name(params);
    var = instance->vars.getSymbol(name);
  }

  if(negVar)
  {
    if(var.type() != VAR_BOOL)
    {  throw parse_exception("Only booleans can be negated!"); }
    else
      var.setType(VAR_NOTBOOL);
  }

  MAYBE_PARSER_INFO("Read variable '" + name + "', internally: " + tostring(var));
  return var;
}

// This function reads an identifier which might be a single variable,
// which includes a fully derefenced matrix, or might be a partially or
// not-at-all dereferenced matrix. It could also just be a number!
// The code shares a lot with readIdentifier, and at some point the two
// should probably merge
template<typename FileReader>
vector<Var> MinionThreeInputReader<FileReader>::readPossibleMatrixIdentifier(FileReader* infile, bool mustBeMatrix) {
  char idChar = infile->peek_char();

  vector<Var> returnVec;

  if ((('0' <= idChar) && ('9' >= idChar)) || idChar == '-') {
    if(mustBeMatrix)
      throw parse_exception("Must be matrix here, not constant");
    DomainInt i = infile->read_num();
    returnVec.push_back(Var(VAR_CONSTANT, i));
    return returnVec;
  }

  bool negVar = false;
  // Check to see if this is a negated Boolean
  if(infile->peek_char() == '!')
  {
    negVar = true;
  // Swallow the '!'
    infile->get_char();
  }

  // Get name of variable.
  string name = infile->get_string();

  Var var = instance->vars.getSymbol(name);

  if(var.type() == VAR_MATRIX)
  {
    vector<DomainInt> params;
    if(infile->peek_char() == '[')
    {
      params = readConstantVector(infile,'[',']',true);
      returnVec = instance->vars.buildVarList(name, params);
      if(negVar)
      {
          if(returnVec.size()!=1)
          {
              throw parse_exception("Sorry, can't negate a matrix");
          }
          if(returnVec[0].type()!=VAR_BOOL)
          {
              throw parse_exception("Sorry, can't negate a non-Boolean variable");
          }
          returnVec[0].setType(VAR_NOTBOOL);
          // The clever thing to do would be to change type of all of returnVec at the end of the function, if necessary.
      }
    }
    else
    { // build a vector of all 'nulls'
        if(negVar)
        {
            throw parse_exception("Sorry, can't negate a matrix");
        }

        vector<DomainInt> maxterms = instance->vars.getMatrixSymbol(name);
        params = vector<DomainInt>(maxterms.size(), -999);
        returnVec = instance->vars.buildVarList(name, params);
    }
    MAYBE_PARSER_INFO("Got matrix:" + tostring(returnVec));
  }
else
{
  if(mustBeMatrix)
    throw parse_exception("Must give matrix here, not single variable!");
  if(negVar)
  {
    if(var.type() != VAR_BOOL)
    { throw parse_exception("Sorry, can't negate a non-Boolean variable"); }
    else
      var.setType(VAR_NOTBOOL);
  }
  returnVec.push_back(var);
}
MAYBE_PARSER_INFO("Read variable '" + name + "', internally: " + tostring(var));
return returnVec;
}

template<typename FileReader>
vector<ConstraintBlob> MinionThreeInputReader<FileReader>::readConstraintList(FileReader* infile) {
  vector<ConstraintBlob> conlist;

  infile->check_sym('{');
  conlist.push_back(readConstraint(infile));

  char delim = infile->get_char();
  while(delim != '}')
  {
    if(delim != ',')
      throw parse_exception(string("Expected '}' or ',' , confused by '") + delim + string("'"));
    conlist.push_back(readConstraint(infile));
    delim = infile->get_char();
  }
  return conlist;
}

/// Reads a vector of variables (which can include constants).
/// Accepts:
/// M (for matrix identifer M)
/// [ M,B,.. ] (for matrix identifers M and variables B)
template<typename FileReader>
vector<Var> MinionThreeInputReader<FileReader>::readLiteralVector(FileReader* infile) {
  vector<Var> newVector;

  if(infile->peek_char() != '[')
  { // Must just be a matrix identifier
    return readPossibleMatrixIdentifier(infile, true);
  }

  infile->check_sym('[');

  // Delim here might end up being "x" or something similar. The reason
  // that we peek it is in case whis is an empty vector.

  char delim = infile->peek_char();

  if(delim == ']')
  {
  // Eat the ']'
    infile->get_char();
    MAYBE_PARSER_INFO("Read empty vector.");
  }
  else
  {
    while (delim != ']') {
      vector<Var> v = readPossibleMatrixIdentifier(infile);
      newVector.insert(newVector.end(), v.begin(), v.end());
      delim = infile->get_char();
      if(delim != ',' && delim != ']')
      {
  // replace X with the character we got.
        string s = "Expected ',' or ']'. Got 'X'.";
        s[s.size() - 3] = delim;
        throw parse_exception(s);
      }

      if(delim == ',' && infile->peek_char() == ']')
        delim = infile->get_char();
    }
  }
  return newVector;
}


template<typename FileReader>
vector<vector<Var> > MinionThreeInputReader<FileReader>::read2DMatrix(FileReader* infile)
{
  vector<vector<Var> > return_vals;

  if(infile->peek_char() != '[')
    return read2DMatrixVariable(infile);

  infile->check_sym('[');

  while(infile->peek_char() != ']')
  {
    MAYBE_PARSER_INFO("Continuing reading matrix, peeked at " + tostring(']'));
    // See if there is an array, or just a variable.
    if(infile->peek_char() == '[')
      return_vals.push_back(readLiteralVector(infile));
    else
    {
      vector<vector<Var> > vars = read2DMatrixVariable(infile);
      for(SysInt i = 0; i < (SysInt)vars.size(); ++i)
        return_vals.push_back(vars[i]);
    }
    // Eat a comma if there is one there.
    if(infile->peek_char() == ',')
      infile->check_sym(',');
  }

  infile->check_sym(']');
  return return_vals;
}

// This function reads the next identifier, which should be a 1D or 2D matrix,
// and returns it (if it was 1D, it returns it as a 1 row 2D matrix.
template<typename FileReader>
vector<vector<Var> > MinionThreeInputReader<FileReader>::read2DMatrixVariable(FileReader* infile) {
  string name = infile->get_string();
  Var var = instance->vars.getSymbol(name);
  // Check it is a matrix
  if(var.type() != VAR_MATRIX)
    throw parse_exception("Expected matrix");
  // Get dimension of matrix.
  vector<DomainInt> indices = instance->vars.getMatrixSymbol(name);
  // Make sure the matrix doesn't have an index after it. This is to produce better error messages.
  if(infile->peek_char() != ',' && infile->peek_char() != ']')
    throw parse_exception("Only accept raw matrix names here, expected ',' next.");

  if(indices.size() == 1)
  {
    vector<DomainInt> terms;
    terms.push_back(-999);
    // Use the existing code to flatten a matrix.
    // make_vec takes a T and turns it into a 1 element vector<T>.
    return make_vec(instance->vars.buildVarList(name, terms));
  }
  else
  {
    return instance->vars.flattenTo2DMatrix(name);
  }
}

// Note: allowNulls maps '_' to -999 (a horrible hack I know).
// That last parameter defaults to false.
// The start and end default to '[' and ']'
template<typename FileReader>
vector<DomainInt> MinionThreeInputReader<FileReader>::readConstantVector
  (FileReader* infile, char start, char end, bool allowNulls)
{
  vector<DomainInt> newVector;
  infile->check_sym(start);

  // The reason we peek here is in case this is an empty vector
  char delim = infile->peek_char();

  if(delim == end)
  {
    // Eat the ']'
    infile->get_char();
    MAYBE_PARSER_INFO("Read empty vector.");
  }
  else
  {
    while (delim != end)
    {
      if(allowNulls && infile->peek_char() == '_')
      {
        infile->get_char();
        newVector.push_back(-999);
      }
      else
        newVector.push_back(infile->read_num()) ;
      delim = infile->get_char();
      if(delim != ',' && delim != end)
        throw parse_exception(string("Expect ',' or ") + end + string("'. Got '") +
        delim + string("'"));
    }
  }
  return newVector;
}

// Note: allowNulls maps '_' to -999 (a horrible hack I know).
// That last parameter defaults to false.
// The start and end default to '[' and ']'
template<typename FileReader>
vector<pair<SysInt,DomainInt> > MinionThreeInputReader<FileReader>::readShortTuple(FileReader* infile)
{
  vector<pair<SysInt, DomainInt> > newVector;
  infile->check_sym('[');

  while(infile->peek_char() == '(')
  {
    infile->check_sym('(');
    SysInt var = checked_cast<SysInt>(infile->read_num());
    infile->check_sym(',');
    DomainInt val = infile->read_num();
    infile->check_sym(')');
    newVector.push_back(make_pair(var, val));
    if(infile->peek_char() == ',')
      infile->check_sym(',');
  }
  infile->check_sym(']');

  return newVector;
}

/// Read an expression of the type ' {<num>..<num>} '
template<typename FileReader>
vector<DomainInt> MinionThreeInputReader<FileReader>::readRange(FileReader* infile)
{
  vector<DomainInt> newVector;
  infile->check_sym('{');

  newVector.push_back(infile->read_num());
  infile->check_sym('.');
  infile->check_sym('.');

  newVector.push_back(infile->read_num());

  infile->check_sym('}');
  return newVector;
}


/// Read a list of tuples
template<typename FileReader>
void MinionThreeInputReader<FileReader>::readShortTuples(FileReader* infile)
{
  while(infile->peek_char() != '*')
  {
    string name = infile->get_string();
    DomainInt num_of_short_tuples = infile->read_num();
    vector<vector<pair<SysInt, DomainInt> > > tups;

    for(DomainInt i = 0; i < num_of_short_tuples; ++i)
      tups.push_back(readShortTuple(infile));

    ShortTupleList* stl = instance->shortTupleListContainer->getNewShortTupleList(tups);
    instance->addShortTableSymbol(name, stl);
  }
}

template<typename FileReader>
void MinionThreeInputReader<FileReader>::readTuples(FileReader* infile)
{
  while(infile->peek_char() != '*')
  {
    string name = infile->get_string();
    DomainInt num_of_tuples = infile->read_num();
    DomainInt tuple_length = infile->read_num();
    MAYBE_PARSER_INFO("Reading tuplelist '" + name + "', length " + tostring(num_of_tuples) +
      ", arity " + tostring(tuple_length) );
    TupleList* tuplelist = instance->tupleListContainer->getNewTupleList(num_of_tuples, tuple_length);
    DomainInt* tuple_ptr = tuplelist->getPointer();
    for(DomainInt i = 0; i < num_of_tuples; ++i)
      for(DomainInt j = 0; j < tuple_length; ++j)
    {
      tuple_ptr[checked_cast<SysInt>(i * tuple_length + j)] = infile->read_num();
    }
    tuplelist->finalise_tuples();
    instance->addTableSymbol(name, tuplelist);

    if(map_long_short_mode != MLTTS_NoMap)
    {
      ShortTupleList* stl = instance->shortTupleListContainer->getNewShortTupleList(tuplelist, map_long_short_mode);
      instance->addShortTableSymbol(name, stl);
    }
  }
}

template<typename FileReader>
void MinionThreeInputReader<FileReader>::readSearch(FileReader* infile) {
  while(infile->peek_char() != '*')
  {
    string var_type = infile->get_string();

    if(var_type == "VARORDER")
    {
      VarOrderEnum vo = ORDER_ORIGINAL;
      bool find_one_sol = false;

      if(infile->peek_char() == 'A')
      {
        string s = infile->get_string();
        if(s != "AUX")
          throw parse_exception("I do not understand " + s);
        find_one_sol = true;
      }

      if(infile->peek_char() != '[')
      {
        string s = infile->get_string();
#define Z(x) if(s == #x) { vo = ORDER_##x; goto found; }
Z(STATIC) Z(SDF) Z(SRF) Z(LDF) Z(ORIGINAL) Z(WDEG) Z(CONFLICT) Z(DOMOVERWDEG)
#undef Z
        if(vo == ORDER_WDEG || vo == ORDER_DOMOVERWDEG)
        {
#ifndef WDEG
          USER_ERROR("This minion was not compiled with support for WDEG or DOMOVERWDEG orderings (add -WDEG to build options)");
#endif
        }
throw parse_exception("Don't understand '" + s + "'");
found: ;
      }

      instance->search_order.push_back(SearchOrder(readLiteralVector(infile), vo, find_one_sol));
      MAYBE_PARSER_INFO("Read var order, length " +
        tostring(instance->search_order.back().var_order.size()));
    }
    else if(var_type == "PERMUTATION")
    {
      if(!instance->permutation.empty())
        throw parse_exception("Can't have two PERMUTATIONs!");
      instance->permutation = readLiteralVector(infile);
      MAYBE_PARSER_INFO("Read permutation, length " +
        tostring(instance->permutation.size()));
    }
    else if(var_type == "SYMORDER")
    {
      if(!instance->sym_order.empty())
        throw parse_exception("Can't have two SYMORDERs!");
      instance->sym_order = readLiteralVector(infile);
      MAYBE_PARSER_INFO("Read Symmetry Ordering, length " +
        tostring(instance->permutation.size()));
    }
    else if(var_type == "VALORDER")
    {
      if(instance->search_order.empty())
        throw parse_exception("Must declare VARORDER first");
      if(!instance->search_order.back().val_order.empty())
        throw parse_exception("Can't have two VALORDERs for a VARORDER");
      vector<ValOrderEnum> valOrder ;

      infile->check_sym('[');

      char delim = infile->peek_char();

      while (delim != ']') {
        char valOrderIdentifier = infile->get_char();
        switch(valOrderIdentifier)
        {
          case 'a':
            valOrder.push_back(VALORDER_ASCEND);
            break;
          case 'd':
            valOrder.push_back(VALORDER_DESCEND);
            break;
          case 'r':
            valOrder.push_back(VALORDER_RANDOM);
            break;

          default:
            throw parse_exception("Expected 'a' or 'd' or 'r'");
        }
        delim = infile->get_char();                                 // , or ]
      }
      instance->search_order.back().val_order = valOrder;

      MAYBE_PARSER_INFO("Read val order, length " +
          tostring(instance->search_order.back().val_order.size()));
    }
    else if(var_type == "MAXIMISING" || var_type == "MAXIMIZING")
    {
      if(instance->is_optimisation_problem == true)
        throw parse_exception("Can only have one min / max per problem!");

      Var var = readIdentifier(infile);
      MAYBE_PARSER_INFO("Maximising " + tostring(var));
      instance->set_optimise(false, var);
    }
    else if(var_type == "MINIMISING" || var_type == "MINIMIZING")
    {
      if(instance->is_optimisation_problem == true)
        throw parse_exception("Can only have one min / max per problem!");

      Var var = readIdentifier(infile);
      MAYBE_PARSER_INFO("Minimising " + tostring(var));
      instance->set_optimise(true, var);
    }
    else if(var_type == "PRINT")
    {
      if(infile->peek_char() == 'A')
      {
        string in = infile->get_string();
        if(in != "ALL")
          throw parse_exception("Don't understand '"+in+"'. Do you mean 'ALL'?");
        print_all_vars = true;
      }
      else if(infile->peek_char() == 'N')
      {
        string in = infile->get_string();
        if(in != "NONE")
          throw parse_exception("Don't understand '"+in+"'. Do you mean 'NONE'?");
        print_all_vars = false;
      }
      else
      {
        print_all_vars = false;
        vector<vector<Var> > new_matrix = read2DMatrix(infile);
        for(SysInt i = 0; i < (SysInt)new_matrix.size(); ++i)
          instance->print_matrix.push_back(new_matrix[i]);
      }
    }
    else if(var_type == "CONSTRUCTION")
    {
      if(!isGadgetReader())
        throw parse_exception("Only have construction sites on gadgets!");

      instance->constructionSite = readLiteralVector(infile);
      MAYBE_PARSER_INFO("Read construction site, size " + tostring(instance->constructionSite.size()));
    }
    else
      {  throw parse_exception("Don't understand '" + var_type + "' as a variable type."); }
  }
}


template<typename FileReader>
void MinionThreeInputReader<FileReader>::readAliasMatrix(FileReader* infile, const vector<DomainInt>& max_indices, vector<DomainInt> indices, string name)
{
  if(infile->peek_char() == '[')
  {
    infile->check_sym('[');
    // Have another level of reading to do..
    indices.push_back(0);
    readAliasMatrix(infile, max_indices, indices, name);
    infile->check_sym(']');
    while(infile->peek_char() == ',')
    {
      infile->check_sym(',');
      ++indices.back();
      infile->check_sym('[');
      readAliasMatrix(infile, max_indices, indices, name);
      infile->check_sym(']');
    }
    if(indices.back() + 1 != max_indices[indices.size() - 1])
      throw parse_exception("Incorrectly sized matrix!, expected index " +
      tostring(indices.size() - 1) + " to have " + tostring(max_indices[indices.size() - 1]) +
      " terms, got " + tostring(indices.back() + 1));
  }
  else
  {
    // Have reached the bottom level!
    indices.push_back(0);
    Var v = readIdentifier(infile);
    instance->vars.addSymbol(name + to_var_name(indices), v);
    while(infile->peek_char() == ',')
    {
      infile->check_sym(',');
      ++indices.back();
      Var v = readIdentifier(infile);
      instance->vars.addSymbol(name + to_var_name(indices), v);
    }
    if(indices.back() + 1 != max_indices[indices.size() - 1])
      throw parse_exception("Incorrectly sized matrix!, expected index " +
      tostring(indices.size() - 1) + " to have " + tostring(max_indices[indices.size() - 1]) +
      " terms, got " + tostring(indices.back() + 1));
  }
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionThreeInputReader<FileReader>::readVars(FileReader* infile) {
  while(infile->peek_char() != '*')
  {
    MAYBE_PARSER_INFO("Begin reading variables");
    string var_type = infile->get_string();

    if(var_type != "BOOL" && var_type != "BOUND" && var_type != "SPARSEBOUND"
      && var_type != "DISCRETE" && var_type != "ALIAS")
      throw parse_exception(string("Unknown variable type: '") + var_type + "'");

    string varname = infile->get_string();
    MAYBE_PARSER_INFO("Name:" + varname);

    bool isArray = false;
    vector<DomainInt> indices;

    if(infile->peek_char() == '[')
    {
      MAYBE_PARSER_INFO("Is array!");
      isArray = true;
      indices = readConstantVector(infile);
      for(UnsignedSysInt i = 0; i < indices.size(); ++i)
        if(indices[i] < 0)
          throw parse_exception("Matrix " + varname + " has a negative size for index " + tostring(i));
      MAYBE_PARSER_INFO("Found " + tostring(indices.size()) + " indices");
    }

    VariableType variable_type = VAR_INVALID;
    vector<DomainInt> domain;

    if(var_type == "ALIAS")
    {
      if(isArray == false)
      {
        infile->check_sym('='); // XYZ
        Var v = readIdentifier(infile);
        instance->vars.addSymbol(varname, v);
      }
      else
      {
        instance->vars.addMatrixSymbol(varname, indices);
        infile->check_sym('=');
        infile->check_sym('[');
        readAliasMatrix(infile, indices, vector<DomainInt>(), varname);
        infile->check_sym(']');
      }
    }
    else if(var_type == "BOOL")
    {
      variable_type = VAR_BOOL;
    }
    else if(var_type == "BOUND")
    {
      variable_type = VAR_BOUND;
      domain = readRange(infile);
      if(domain[0]>domain[1])
        throw parse_exception("Range in decreasing order e.g. 1..0  in declaration of BOUND variable.");
      if(domain.size() != 2)
        throw parse_exception("Ranges contain 2 numbers!");
    }
    else if(var_type == "DISCRETE")
    {
      variable_type = VAR_DISCRETE;
      domain = readRange(infile);
      if(domain[0]>domain[1])
        throw parse_exception("Range in decreasing order e.g. 1..0  in declaration of BOUND variable.");
      if(domain.size() != 2)
        throw parse_exception("Ranges contain 2 numbers!");
    }
    else if(var_type == "SPARSEBOUND")
    {
      variable_type = VAR_SPARSEBOUND;
      domain = readConstantVector(infile, '{', '}');

      for(unsigned int i=0; i<(SysInt)domain.size()-1; i++) {
          if(domain[i]>domain[i+1]) {
              throw parse_exception("Values out of order in SPARSEBOUND domain.");
          }
          if(domain[i]==domain[i+1]) {
              throw parse_exception("Repeated values in SPARSEBOUND domain.");
          }
      }
      if(domain.size() < 1)
        throw parse_exception("Don't accept empty domains!");
    }
    else
      throw parse_exception("I don't know about var_type '" + var_type + "'");

    if(var_type != "ALIAS")
    {
      if(isArray)
      {
        instance->vars.addMatrixSymbol(varname, indices);
        // If any index is 0, don't add any variables.
        if(find(indices.begin(), indices.end(), 0) == indices.end())
        {
          vector<DomainInt> current_index(indices.size(), 0);
          MAYBE_PARSER_INFO("New Var: " + varname + to_var_name(current_index));
          instance->vars.addSymbol(varname + to_var_name(current_index),
            instance->vars.getNewVar(variable_type, domain));
          while(increment_vector(current_index, indices))
          {
            MAYBE_PARSER_INFO("New Var: " + varname + to_var_name(current_index));
            instance->vars.addSymbol(varname + to_var_name(current_index),
              instance->vars.getNewVar(variable_type, domain));
          }

          vector<vector<Var> > matrix_list = instance->vars.flattenTo2DMatrix(varname);
          for(SysInt i = 0; i < (SysInt)matrix_list.size(); ++i)
            instance->all_vars_list.push_back(matrix_list[i]);
        }
      }
      else
      {
        Var v = instance->vars.getNewVar(variable_type, domain);
        instance->vars.addSymbol(varname, v);
        instance->all_vars_list.push_back(make_vec(v));
      }
    }
  }

}
