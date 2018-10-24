#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/constraint_modulo.h"
#include "../constraints/constraint_checkassign.h"
#include "../constraints/forward_checking.h"

template<typename V1, typename V2>
inline AbstractConstraint*
BuildCT_MODULO(StateObj* stateObj, const V1& vars, const V2& var2, ConstraintBlob&)
{
  D_ASSERT(vars.size() == 2);
  D_ASSERT(var2.size() == 1);
  /*if(vars[0].getInitialMin() < 0 || vars[1].getInitialMin() < 1 ||
         var2[0].getInitialMin() < 0)
  {
    typedef SlowModConstraint<typename V1::value_type, typename V1::value_type, typename V2::value_type, false> ModCon;
    return new CheckAssignConstraint<ModCon, false>(stateObj, ModCon(stateObj, vars[0], vars[1], var2[0]));
  }
  else
    return new ModConstraint<typename V1::value_type, typename V1::value_type,
                             typename V2::value_type>(stateObj, vars[0], vars[1], var2[0]);*/
  // Do FC 
  typedef SlowModConstraint<typename V1::value_type, typename V1::value_type, typename V2::value_type, false> ModCon;
  AbstractConstraint* modct=new CheckAssignConstraint<ModCon, false>(stateObj, ModCon(stateObj, vars[0], vars[1], var2[0]));
  return forwardCheckingCon(stateObj, modct);
  
}

BUILD_CT(CT_MODULO, 2)
