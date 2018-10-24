#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/reify.h"

template<typename VarArray>
inline AbstractConstraint*
BuildCT_REIFY(StateObj* stateObj, const VarArray& vars, ConstraintBlob& bl)
{
  switch(bl.internal_constraints[0].constraint->type)
  {
    case CT_EQ:
    {
      ConstraintBlob blob(bl.internal_constraints[0]);
      blob.vars.push_back(make_vec(bl.vars[0][0]));
      blob.constraint = get_constraint(CT_EQ_REIFY);
      return build_constraint(stateObj, blob);
    }
    case CT_DISEQ:
    {
      ConstraintBlob blob(bl.internal_constraints[0]);
      blob.vars.push_back(make_vec(bl.vars[0][0]));
      blob.constraint = get_constraint(CT_DISEQ_REIFY);
      return build_constraint(stateObj, blob);
    }
    case CT_MINUSEQ:
    {
      ConstraintBlob blob(bl.internal_constraints[0]);
      blob.vars.push_back(make_vec(bl.vars[0][0]));
      blob.constraint = get_constraint(CT_MINUSEQ_REIFY);
      return build_constraint(stateObj, blob);
    }
    default:
      return reifyCon(stateObj, build_constraint(stateObj, bl.internal_constraints[0]), vars[0]);
  }
}

BUILD_CT(CT_REIFY, 1)
