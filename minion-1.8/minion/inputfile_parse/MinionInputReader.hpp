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

// Plan here is to generate an instance of a problem (or whatever you have)
// and return that.

/// TODO: We need somewhere better to put these things.

#include <string>

#include "MinionInputReader.h"

//ConstraintDef* constraint_list;

// Defined in MinionThreeInputReader, cos it can only be in one place.
extern SysInt num_of_constraints;

template<typename T>
typename T::value_type& index(T& container, DomainInt index_pos)
{
  if(index_pos < 0 || index_pos >= (SysInt)container.size())
    throw parse_exception("Index position " + tostring(index_pos) +
                          " out of range");
  return container[checked_cast<SysInt>(index_pos)];
}

template<typename FileReader>
void MinionInputReader<FileReader>::parser_info(string s)
{
  if(parser_verbose)
    cout << s << endl;
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// flatten
// type: m (2d matrix), t (3d matrix)
// Flattening is row-wise (2d), plane-wise row-wise (3d).
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
vector<Var> MinionInputReader<FileReader>::flatten(char type, SysInt index) {
  UnsignedSysInt rowIndex, colIndex, planeIndex ;
  vector<Var> flattened ;
  // flatten row-wise
  if (type == 'm') {
    vector< vector<Var> > matrix = Matrices.at(index) ;
    for (rowIndex = 0; rowIndex < matrix.size() ; rowIndex++) {
      vector<Var> row = matrix.at(rowIndex) ;
      for (colIndex = 0; colIndex < row.size(); colIndex++)
        flattened.push_back(row.at(colIndex)) ;
    }
  }
  // flatten plane-wise then row-wise
  else {
    vector< vector <vector <Var> > > tensor = Tensors.at(index) ;
    for (planeIndex = 0; planeIndex < tensor.size(); planeIndex++) {
      vector< vector <Var> > plane = tensor.at(planeIndex) ;
      for (rowIndex = 0; rowIndex < plane.size(); rowIndex++) {
        vector<Var> row = plane.at(rowIndex) ;
        for (colIndex = 0; colIndex < row.size(); colIndex++)
          flattened.push_back(row.at(colIndex)) ;
      }
    }
  }
  return flattened ;
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// getColOfMatrix
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
vector<Var> MinionInputReader<FileReader>::getColOfMatrix(vector<vector<Var> >& matrix, SysInt colNo) {
  vector<Var> result ;
  for (UnsignedSysInt rowIndex = 0; rowIndex < matrix.size(); rowIndex++) {
    result.push_back(matrix.at(rowIndex).at(colNo)) ;
  }
  return result ;
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// getRowThroughTensor
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
vector<Var> MinionInputReader<FileReader>::getRowThroughTensor(
                                                   vector< vector< vector<Var> > >& tensor, SysInt rowNo, SysInt colNo) {
  vector<Var> result ;
  for (UnsignedSysInt planeIndex = 0; planeIndex < tensor.size() ; planeIndex ++) {
    vector< vector<Var> >& plane = tensor.at(planeIndex) ;
    vector<Var>& row = plane.at(rowNo) ;
    result.push_back(row.at(colNo)) ;
  }
  return result ;
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// read
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionInputReader<FileReader>::read(FileReader* infile) {

  while(infile->peek_char() == '#')
    parser_info(string("Read comment line:") + infile->getline());


    readVars(infile) ;
    readVarOrder(infile) ;
    readValOrder(infile) ;
    readMatrices(infile) ;

    // At this point, may or may not have a tuples entry
    {
      string s = infile->get_string();
      if(s == "tuplelists")
      {
        readTuples(infile);
        s = infile->get_string();
      }
      if(s == "objective")
        readObjective(infile);
      else
      {
        throw parse_exception("I don't understand: " + s + ". Did you mean "
                                  " tuplelists or objective?");
      }
    }
    readPrint(infile);

    while(readConstraint(infile, false)) ;

}



//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readConstraint
// Recognise constraint by its name, read past name and leading '('
// Return false if eof or unknown ct. Else true.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
BOOL MinionInputReader<FileReader>::readConstraint(FileReader* infile, BOOL reified) {
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
      parser_info("Done.") ;
      return false;
    }
    else
    { throw parse_exception(string("Unknown Constraint: '") + id + string("'")); }
  }
  ConstraintDef* constraint = constraint_list + constraint_num;

  switch(constraint->type)
  {
#ifdef CT_WATCHED_TABLE_ABC
    case CT_WATCHED_TABLE:
      readConstraintTable(infile, get_constraint(constraint->type));
      break;
#endif
#ifdef CT_WATCHED_NEGATIVE_TABLE_ABC
    case CT_WATCHED_NEGATIVE_TABLE:
      readConstraintTable(infile, get_constraint(constraint->type));
      break;
#endif

    default:
      readGeneralConstraint(infile, constraint);
  }

  instance->bounds_check_last_constraint();
  return true ;
}


template<typename FileReader>
void MinionInputReader<FileReader>::readGeneralConstraint(FileReader* infile, ConstraintDef* def)
{
  // This slightly strange code is to save copying the ConstraintBlob as much as possible.
  instance->add_constraint(ConstraintBlob(def));
  ConstraintBlob& con = instance->constraints.back();
  vector<vector<Var> >& varsblob = instance->constraints.back().vars;
  vector<vector<DomainInt> >& constblob = instance->constraints.back().constants;

  for(SysInt i = 0; i < def->number_of_params; ++i)
  {
    switch(def->read_types[i])
    {
      case read_list:
        varsblob.push_back(readVectorExpression(infile));
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
        case read_constraint:
        {
          // This slightly weird way of getting a constraint is to avoid having to make
          // significant changes to the old parser.
      readConstraint(infile, false);
      ConstraintBlob new_con = instance->constraints.back();
      instance->constraints.pop_back();
      con.internal_constraints.push_back(new_con);
    }
    break;
    case read_constraint_list:
    throw parse_exception("Watched or is not supported by this parser. Use 'MINION 3'");
      case read_constant:
        constblob.push_back(make_vec(infile->read_num()));
        break;
      case read_constant_list:
      {
        vector<Var> vectorOfConst(readVectorExpression(infile)) ;
    vector<DomainInt> vals;
        for(UnsignedSysInt loop = 0; loop < vectorOfConst.size(); ++loop)
        {
          if(vectorOfConst[loop].type() != VAR_CONSTANT)
              throw parse_exception("Vector must only contain constants.");
            else
        vals.push_back(vectorOfConst[loop].pos());
        }
        constblob.push_back(vals);
      }
        break;
      default:
        D_FATAL_ERROR("short tuple constraints cannot be used in a MINION 1 or 2 format file. Sorry.");
    }
    if(i != def->number_of_params - 1)
      infile->check_sym(',');
  }
  infile->check_sym(')');
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readConstraintElement
// element(vectorofvars, indexvar, var)
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionInputReader<FileReader>::readConstraintElement(FileReader* infile, ConstraintDef* ctype) {
  parser_info("reading an element ct. " ) ;
  vector<vector<Var> > vars;
  // vectorofvars
  vars.push_back(readVectorExpression(infile));
  infile->check_sym(',');
  // indexvar
  vars.push_back(make_vec(readIdentifier(infile)));
  infile->check_sym(',');
  // The final var is shoved on the end of the vector of vars as it should
  // be of a similar type.
  // final var
  vars[0].push_back(readIdentifier(infile));
  infile->check_sym(')');
  instance->add_constraint(ConstraintBlob(ctype, vars));
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readConstraintTable
// table(<vectorOfVars>, {<tuple> [, <tuple>]})
// Tuples represented as a vector of SysInt arrays.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionInputReader<FileReader>::readConstraintTable(FileReader* infile, ConstraintDef* def)
{
  parser_info( "reading a table ct (unreifiable)" ) ;

  char delim = ' ';
  SysInt count;
  vector<Var> vectorOfVars(readVectorExpression(infile)) ;
  SysInt tupleSize = vectorOfVars.size() ;

  infile->check_sym(',');

  char next_char = infile->peek_char();
  if(next_char != 't' && next_char != '{')
    throw parse_exception("Expected either 't' or a tuple list");

  TupleList* tuplelist;

  if(next_char == 't')
  {
    infile->check_sym('t');
    DomainInt tuple_num = infile->read_num();
    if(tuple_num >= instance->tupleListContainer->size())
    {
      throw parse_exception("There are only " + tostring(instance->tupleListContainer->size()) +
                                " tuplelists, requested " + tostring(tuple_num) + ".");
    }
    tuplelist = instance->tupleListContainer->getTupleList(tuple_num);
  }
  else
  {
    vector<vector<DomainInt> > tuples ;
    infile->check_sym('{');
    while (delim != '}')
    {
      infile->check_sym('<');
      vector<DomainInt> tuple(tupleSize);
      DomainInt elem = infile->read_num() ;
      tuple[0] = elem ;
      for (count = 1; count < tupleSize; count++)
      {
        infile->check_sym(',');
        elem = infile->read_num() ;
        tuple[count] = elem ;
      }
      infile->check_sym('>');
      tuples.push_back(tuple) ;
      delim = infile->get_char();                          // ',' or '}'
      if(delim != ',' && delim!= '}')
        throw parse_exception("Expected ',' or '}'");
    }
    tuplelist = instance->tupleListContainer->getNewTupleList(tuples);
  instance->addUnnamedTableSymbol(tuplelist);
  }

    infile->check_sym(')');
    ConstraintBlob tableCon(def, vectorOfVars);
    tableCon.tuples = tuplelist;
    instance->add_constraint(tableCon);
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readIdentifier
// Expects "<idChar><index>", where <idChar> is 'x', 'v', 'm', 't'.
// Assumes caller knows what idChar should be.
// Returns an object of type Var.
// NB peek() does not ignore whitespace, >> does. Hence use of putBack()
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
Var MinionInputReader<FileReader>::readIdentifier(FileReader* infile) {
  char idChar = infile->peek_char();
  if ((('0' <= idChar) && ('9' >= idChar)) || idChar == '-') {
    DomainInt i = infile->read_num();
    return Var(VAR_CONSTANT, i);
  }
  DomainInt index = -1 ;

  if(idChar != 'x' && idChar != 'n')
  {
    string s("Found 'X', expected 'x' or 'n' at start of a variable");
    s[7] = idChar;
    throw parse_exception(s);
  }

  if(idChar == 'x')
  {
    // Eat the 'x'.
    infile->check_sym('x');
    index = infile->read_num();
    return instance->vars.get_var(idChar, index);
  }

  // Must have found an 'n'.
  infile->check_sym('n');
  infile->check_sym('x');
  index = infile->read_num();
  Var var = instance->vars.get_var(idChar, index);
  if(var.type() != VAR_BOOL)
    throw parse_exception("Can only 'not' a Boolean variable!");
  var.setType(VAR_NOTBOOL);
  return var;
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readLiteralMatrix
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
vector< vector<Var> > MinionInputReader<FileReader>::readLiteralMatrix(FileReader* infile) {

  infile->check_sym('[');

  // Delim here might end up being "x" or something similar. The reason
  // that we peek it is in case whis is an empty vector.
  char delim = infile->peek_char();
  vector< vector<Var> > newMatrix ;

  if(delim == ']')
  {
    // Eat the ']'
    infile->get_char();
    parser_info("Read empty matrix");
  }
  else
  {
    while(delim != ']') {
      newMatrix.push_back(readLiteralVector(infile)) ;
      delim = infile->get_char();            // , or ]
    }
  }
  return newMatrix ;
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readLiteralVector
// of vars or consts. Checks 1st elem of vect (empty vects not expected)
//  to see which.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
vector<Var> MinionInputReader<FileReader>::readLiteralVector(FileReader* infile) {
  vector<Var> newVector ;
  infile->check_sym('[');

  // Delim here might end up being "x" or something similar. The reason
  // that we peek it is in case whis is an empty vector.

  char delim = infile->peek_char();

  if(delim == ']')
  {
    // Eat the ']'
    infile->get_char();
    parser_info("Read empty vector.");
  }
  else
  {
    while (delim != ']') {
      newVector.push_back(readIdentifier(infile)) ;
      delim = infile->get_char();
         if(delim != ',' && delim != ']')
         {
           // replace X with the character we got.
           string s = "Expected ',' or ']'. Got 'X'.";
           s[s.size() - 3] = delim;
           throw parse_exception(s);
         }
      }
  }
  // Squeeze vector to smallest size.
  return vector<Var>(newVector.begin(), newVector.end());

}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readMatrices
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionInputReader<FileReader>::readMatrices(FileReader* infile) {
  char delim ;
  // Read Vectors%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  DomainInt noOfMatrixType = infile->read_num();
  if(parser_verbose)
    cout << "Number of 1d vectors: " << noOfMatrixType << endl ;
  for (DomainInt count1 = 0; count1 < noOfMatrixType; ++count1)
    Vectors.push_back(readLiteralVector(infile)) ;
  // Read 2dMatrices%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  noOfMatrixType = infile->read_num();
  if(parser_verbose)
    cout << "Number of 2d matrices: " << noOfMatrixType << endl ;
  for (DomainInt count1 = 0; count1 < noOfMatrixType; ++count1)
    Matrices.push_back(readLiteralMatrix(infile)) ;
  // Read 3dMatrices%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  noOfMatrixType = infile->read_num();
  if(parser_verbose)
    cout << "Number of 3d tensors: " << noOfMatrixType << endl ;
  for (DomainInt count1 = 0; count1 < noOfMatrixType; ++count1) {
    vector< vector< vector <Var> > > newTensor ;
    delim = infile->get_char();                                           // [
    while (delim != ']') {
      newTensor.push_back(readLiteralMatrix(infile)) ;
      delim = infile->get_char();                                    // , or ]
    }
    Tensors.push_back(newTensor) ;
  }
}

//%%%%
// readTuples
// 'tuplelists' <val>  ( <num_tuples> <tuple_length> <vals> ...
template<typename FileReader>
void MinionInputReader<FileReader>::readTuples(FileReader* infile) {
  DomainInt tuple_count = infile->read_num();
  for(DomainInt counter = 0; counter < tuple_count; ++counter)
  {
    DomainInt num_of_tuples = infile->read_num();
    DomainInt tuple_length = infile->read_num();
    TupleList* tuplelist = instance->tupleListContainer->getNewTupleList(num_of_tuples, tuple_length);
    instance->addUnnamedTableSymbol(tuplelist);
    DomainInt* tuple_ptr = tuplelist->getPointer();
    for(DomainInt i = 0; i < num_of_tuples; ++i)
      for(DomainInt j = 0; j < tuple_length; ++j)
      {
        tuple_ptr[checked_cast<SysInt>(i * tuple_length + j)] = infile->read_num();
      }
    tuplelist->finalise_tuples();
  }
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readObjective
// 'objective' 'none' | 'minimising' <var> | 'maximising' <var>
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionInputReader<FileReader>::readObjective(FileReader* infile) {
  // Note that we will have read "objective" before entering this function.
  string s = infile->get_string();
  if(s == "none")
  {
    parser_info( "objective none" );
    return;
  }

  if(s != "minimising" && s != "maximising")
  {
    throw parse_exception(string("Expected 'none', 'maximising'") +
                              string("or 'minimising'. Got '") + s + "'");
  }

  BOOL minimising = (s == "minimising");
  Var var = readIdentifier(infile) ;
  if(parser_verbose)
    cout << ((minimising) ? "minimising" : "maximising") << var << endl ;
  instance->set_optimise(minimising, var);
}

template<typename FileReader>
void MinionInputReader<FileReader>::readPrint(FileReader* infile) {
  string s = infile->get_string();
  if(s != "print")
    throw parse_exception(string("Expected 'print', recieved '")+s+"'");

  char letter = infile->get_char();
  if(letter == 'n')
  {
    s = infile->get_string();
    if(s != "one")
      throw parse_exception(string("I don't understand '")+s+"'");
    parser_info( "print none" );
    return;
  }
  else if(letter == 'a')
  {
    s = infile->get_string();
    if(s != "ll")
      throw parse_exception(string("I don't understand '" + s + "'"));

    instance->print_matrix = make_vec(instance->vars.get_all_vars());
    return;
  }
  else if(letter == 'm')
  {
    DomainInt matrix_num = infile->read_num();
    instance->print_matrix = index(Matrices, matrix_num);
    if(parser_verbose)
      cout << "print m" << matrix_num << endl;
    return;
  }
  else if(letter == 'v')
  {
    DomainInt vec_num = infile->read_num();
    instance->print_matrix = make_vec(index(Vectors, vec_num));
    if(parser_verbose)
      cout << "print v" << vec_num << endl;
    return;
  }

  throw parse_exception(string("I don't understand this print statement"));
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readValOrder
// '[' <valOrderIdentifier> [, <valOrderIdentifier>]* ']'
// <valOrderIdentifier> := 'a' | 'd' --- for ascending/descending
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionInputReader<FileReader>::readValOrder(FileReader* infile) {
  parser_info( "Reading val order" ) ;

  infile->check_sym('[');

  // Delim here might end up being "x" or something similar. The reason
  // that we peek it is in case whis is an empty vector.

  char delim = infile->peek_char();

  vector<ValOrderEnum> valOrder ;

  if(delim == ']')
  {
    // Eat the ']'
    infile->get_char();
    parser_info("No val order");
  }
  else
  {
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

    ostringstream s;
    s << "Read val order. Length: " << valOrder.size();
    parser_info(s.str());
  }

  if(valOrder.empty())
  {
    parser_info("No value order given, generating automatically");
    valOrder = vector<ValOrderEnum>(instance->search_order[0].var_order.size(), VALORDER_ASCEND);
  }
  instance->search_order[0].val_order = valOrder;

  for(SysInt i = 0; i < (SysInt)instance->search_order.size(); ++i)
    instance->search_order[i].setupValueOrder();
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readVarOrder
// '[' <var> [, <var>]* ']'
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionInputReader<FileReader>::readVarOrder(FileReader* infile) {
  parser_info( "Reading var order" ) ;
  vector<Var> varOrder = readLiteralVector(infile);
  set<Var> used_variables(varOrder.begin(), varOrder.end());

  ostringstream s;
  s << "Read var order. Length: " << varOrder.size();



  parser_info("No order generated, auto-generating complete order");
  SysInt var_count = 0;
  var_count += instance->vars.BOOLs;
  for(UnsignedSysInt i = 0; i < instance->vars.bound.size(); ++i)
    var_count += instance->vars.bound[i].first;
  for(UnsignedSysInt i = 0; i < instance->vars.sparse_bound.size(); ++i)
    var_count += instance->vars.sparse_bound[i].first;
  for(UnsignedSysInt i = 0; i < instance->vars.discrete.size(); ++i)
    var_count += instance->vars.discrete[i].first;
  for(UnsignedSysInt i = 0; i < instance->vars.sparse_discrete.size(); ++i)
    var_count += instance->vars.sparse_discrete[i].first;

  varOrder.reserve(var_count);

  vector<Var> extra_vars;

  if(varOrder.empty())
  {
    for(SysInt i = 0; i < var_count; ++i)
      varOrder.push_back(instance->vars.get_var('x',i));
  }
  else
  {
    for(SysInt i = 0; i < var_count; ++i)
    {
      Var v = instance->vars.get_var('x', i);
      if(used_variables.count(v) == 0)
      {
        extra_vars.push_back(v);
      }
    }
  }

  instance->search_order.push_back(varOrder);
  if(!extra_vars.empty() && ensure_branch_on_all_vars)
  {
    instance->search_order.push_back(extra_vars);
    instance->search_order.back().find_one_assignment = true;
  }



  parser_info(s.str());
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
void MinionInputReader<FileReader>::readVars(FileReader* infile) {
  DomainInt lb, ub, count ;
  char delim ;
  ProbSpec::VarContainer var_obj;
  // Read 01Vars%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  DomainInt noOfVarType = infile->read_num();
  if(parser_verbose)
    cout << "Number of 01 Vars: " << noOfVarType << endl ;
  var_obj.BOOLs = checked_cast<SysInt>(noOfVarType);


  // **** Construct this many 01Vars
  // Read Bounds Vars%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  noOfVarType = infile->read_num();
  if(parser_verbose)
    cout << "Number of Bounds Vars: " << noOfVarType << endl ;
  while (noOfVarType > 0) {
    lb = infile->read_num();
    ub = infile->read_num();
    if(lb > ub)
      throw parse_exception("Lower bound must be less than upper bound!");
    count = infile->read_num();
    if(parser_verbose)
      cout << count << " of " << lb << ", " << ub << endl ;
    var_obj.bound.push_back(make_pair(checked_cast<SysInt>(count), Bounds(lb, ub)));
    noOfVarType -= count ;
  }

  // Read Sparse Bounds Vars%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  noOfVarType = infile->read_num();
  if(parser_verbose)
    cout << "Number of Sparse Bounds Vars: " << noOfVarType << endl ;
  DomainInt domainElem ;
  while (noOfVarType > 0) {
    vector<DomainInt> domainElements ;
    delim = infile->get_char();                                 // {
    while (delim != '}') {
      domainElem = infile->read_num();
      domainElements.push_back(domainElem) ;
      size_t dom_size = domainElements.size();
      if(dom_size > 1)
      {
        if(domainElements[dom_size-1] <= domainElements[dom_size-2])
          throw parse_exception("Domains must be ordered!");
      }
      delim = infile->get_char();                               // , or }
    }
    count = infile->read_num();
    if(parser_verbose)
      cout << count << " of these " << endl ;
    // **** Construct this many discrete vars.
    var_obj.sparse_bound.push_back(make_pair(checked_cast<SysInt>(count), domainElements));
    noOfVarType -= count ;
  }

  // Read Discrete Bounds Vars%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  noOfVarType = infile->read_num();
  if(parser_verbose)
    cout << "Number of Discrete Vars: " << noOfVarType << endl ;
  while (noOfVarType > 0) {
    lb = infile->read_num();
    ub = infile->read_num();
    count = infile->read_num();
    if(lb > ub)
      throw parse_exception("Lower bound must be less than upper bound!");
    if(parser_verbose)
      cout << count << " of " << lb << ", " << ub << endl ;
    var_obj.discrete.push_back(make_pair(checked_cast<SysInt>(count), Bounds(lb, ub)));
    // **** Construct this many discrete bounds vars.
    noOfVarType -= count ;
  }
  // Read Discrete Vars%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  noOfVarType = infile->read_num();
  if(parser_verbose)
    cout << "Number of Sparse Discrete Vars: " << noOfVarType << endl ;
  while (noOfVarType > 0) {
    vector<DomainInt> domainElements ;
    delim = infile->get_char();                                 // {
    while (delim != '}') {
      domainElem = infile->read_num();
      domainElements.push_back(domainElem) ;
      delim = infile->get_char();                             // , or }
    }
    count = infile->read_num();
    if(parser_verbose)
      cout << count << " of these " << endl ;
    // **** Construct this many discrete vars.
    var_obj.sparse_discrete.push_back(make_pair(checked_cast<SysInt>(count), domainElements));
    noOfVarType -= count ;
  }

  instance->vars = var_obj;
}

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
// readVectorExpression
// literal vector (of vars or consts), vi, mi(flattened), ti(flattened),
// row(mi, r), col(mi, c), col(ti, p, c), rowx(ti, p, r), rowz(ti, r, c)
// NB Expects caller knows whether vars or consts expected for lit vect.
// NB peek does not ignore wspace, >> does. Hence use of putback
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
template<typename FileReader>
vector<Var> MinionInputReader<FileReader>::readVectorExpression(FileReader* infile) {
  SysInt row, col, plane ;
  SysInt input_val;
  char idChar = infile->get_char();
  switch (idChar) {
    case '[':
      parser_info( "Reading Literal Vector of vars or consts" ) ;
      infile->putback(idChar) ;
      return readLiteralVector(infile) ;
    case 'v':                                        // vector identifier
      parser_info( "Reading vector identifier" ) ;
      //infile->putback(idChar) ;
      input_val = infile->read_int();
      return Vectors.at(input_val) ;
    case 'm':                                       // matrix identifier
      parser_info( "Reading matrix identifier (will flatten)" ) ;
      //infile->putback(idChar) ;
      input_val = infile->read_int();
      return flatten('m', input_val) ;
    case 't':                                        // matrix identifier
      parser_info( "Reading tensor identifier (will flatten)" ) ;
      //infile->putback(idChar) ;
      input_val = infile->read_int();
      return flatten('t', input_val) ;
    case 'r':                                       // row of a mx/tensor
      infile->check_sym('o');
      infile->check_sym('w');
      idChar = infile->get_char();            // o w [( x or z]
      switch(idChar) {
        case '(':                                        // row of a matrix
        {parser_info( "Reading row of a matrix" ) ;
          infile->check_sym('m');
          input_val = infile->read_int();
          vector< vector<Var> > matrix = Matrices.at(input_val) ;
          infile->get_char();
          row = infile->read_int();
          infile->get_char();
          return matrix.at(row) ;}
        case 'x':                             // row of a plane of a tensor
        {parser_info( "Reading row of a plane of a tensor" ) ;
          infile->check_sym('(');
          infile->check_sym('t');
          input_val = infile->read_int();
          vector< vector< vector<Var> > >& tensor = Tensors.at(input_val) ;
          infile->check_sym(',');
          input_val = infile->read_int();
          vector< vector <Var> >& tensorPlane = tensor.at(input_val) ;
          infile->check_sym(',');
          input_val = infile->read_int();
          infile->check_sym(')');
          return tensorPlane.at(input_val);
        }
        case 'z':                         // Row through planes of a tensor
        {parser_info( "Reading row through planes of a tensor" ) ;
          infile->check_sym('(');
          input_val = infile->read_int();
          vector< vector< vector<Var> > >& tensor = Tensors.at(input_val) ;
          infile->check_sym(',');
          row = infile->read_int();
          infile->check_sym(',');
          col = infile->read_int();
          infile->check_sym(')');
          return getRowThroughTensor(tensor, row, col) ;}
        default:
          throw parse_exception("Malformed Row Expression");
          break ;
      }
        break ;
      //col(mi, c), col(ti, p, c)
    case 'c':                                        // col of a mx/tensor
      infile->check_sym('o');
      infile->check_sym('l');
      infile->check_sym('(');
      if(infile->peek_char() == 'm') {
        parser_info( "Reading col of matrix" ) ;
        infile->check_sym('m');
        input_val = infile->read_int();
        vector< vector<Var> >& matrix = Matrices.at(input_val) ;
        infile->check_sym(',');
        col = infile->read_int();
        infile->check_sym(')');
        return getColOfMatrix(matrix, col) ;
      }
        else {
          parser_info( "Reading col of tensor" ) ;
          infile->check_sym('t');
          input_val = infile->read_int();
          vector< vector< vector<Var> > >& tensor = Tensors.at(input_val);
          infile->check_sym(',');
          plane = infile->read_int();
          infile->check_sym(',');
          col = infile->read_int();
          infile->check_sym(')');
          return getColOfMatrix(tensor.at(plane), col) ;
        }
      default:
        throw parse_exception("Malformed Vector Expression") ;
        break ;
  }
  FAIL_EXIT("?");
}
