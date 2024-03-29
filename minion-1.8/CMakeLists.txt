cmake_minimum_required(VERSION 2.4.7 FATAL_ERROR)

project(MinionProject)

# This will be ignored by everything except mac
set(MACOSX_DEPLOYMENT_TARGET 10.5)

set(CMAKE_MODULE_PATH ${PROJECT_SOURCE_DIR}/cmake-modules)
include(CheckIncludeFileCXX)
include(CheckCXXCompilerFlag)

set(CMAKE_ALLOW_LOOSE_LOOP_CONSTRUCTS true)

if(TINY_TEST)
    set(CONSTRAINTS "reify;reifyimply;watchelement;eq;sumgeq;true;watched-or")
endif()

include(constraints)
include(CheckIncludeFiles)

set(BUILD_DEF_FILE "${PROJECT_SOURCE_DIR}/minion/BuildDefines.h")
file(REMOVE ${BUILD_DEF_FILE})

if(${PROJECT_SOURCE_DIR} STREQUAL ${PROJECT_BINARY_DIR})
    message(FATAL_ERROR "You cannot run CMake in the source directory (please take a look at the README).")
endif()

set(HGVERSION "0")
set(HGDATE "unknown")
find_program(HG hg)
if(HG)
    execute_process(COMMAND ${HG} parent --template="{node|short}"
            WORKING_DIRECTORY ${PROJECT_SOURCE_DIR}
            OUTPUT_VARIABLE HGVERSIONTMP OUTPUT_STRIP_TRAILING_WHITESPACE)
    execute_process(COMMAND ${HG} parent --template="{date|isodate}"
            WORKING_DIRECTORY ${PROJECT_SOURCE_DIR}
            OUTPUT_VARIABLE HGDATETMP OUTPUT_STRIP_TRAILING_WHITESPACE)
    if(HGDATETMP STREQUAL "")
        message(STATUS "Unable to determine HG version (no checkout?)")
    else()
        set(HGDATE ${HGDATETMP})
        set(HGVERSION ${HGVERSIONTMP})
    endif()
    message(STATUS "HG version ${HGVERSION}, date ${HGDATE}")
else()
    message(STATUS "No HG executable found, unable to determine HG version and date")
endif()

set(CFLAGS "${CFLAGS} -std=gnu++11")

file(APPEND ${BUILD_DEF_FILE} "#define HG_VER ${HGVERSION}\n")
file(APPEND ${BUILD_DEF_FILE} "#define HG_DATE ${HGDATE}\n")

# if we have a bash, generate constraints and help if necessary and add build
# targets to do that
if(UNIX)
    find_program(BASH bash)
    if(BASH)
        add_custom_target(generate-help ${BASH} "minion/help/genhelp.sh" > "minion/help/help.cpp"
                  WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}"
                  COMMENT "generate-help" VERBATIM)
        if(EXISTS "${PROJECT_SOURCE_DIR}/minion/help/help.cpp")
            message(STATUS "Help file exists, not regenerating")
        else()
            message(STATUS "Generating help")
            execute_process(COMMAND ${BASH} "minion/help/genhelp.sh"
                    WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}"
                    OUTPUT_FILE "minion/help/help.cpp")
            message(STATUS "Generating help - done")
        endif()
    else()
        message(STATUS "No bash executable found, not generating help")
    endif()
else()
    message(STATUS "You need a UNIX-like system to regenerate help")
endif()

if(CONSTRAINTS)
    select_constraints(${CONSTRAINTS})
elseif(CONSTRAINTS_FILE)
    file(READ ${CONSTRAINTS_FILE} CONSTRAINTS_FILE_CONTENTS)
    string(REGEX MATCHALL "([^a-z_-]|^)[a-z_-]+\\(" FINDS ${CONSTRAINTS_FILE_CONTENTS})
    foreach(FIND ${FINDS})
        string(REGEX REPLACE "[^a-z_-]" "" CONSTRAINT ${FIND})
        set(FOUND False)
        foreach(LIST_CONSTRAINT ${CONSTRAINTS})
            if(${LIST_CONSTRAINT} STREQUAL ${CONSTRAINT})
                set(FOUND True)
            endif()
        endforeach()
        if(NOT FOUND)
            list(APPEND CONSTRAINTS ${CONSTRAINT})
        endif()
    endforeach()
    select_constraints(${CONSTRAINTS})
else()
    select_constraints(${ALL_CONSTRAINTS})
endif()

file(GLOB SRCS "${PROJECT_SOURCE_DIR}/minion/build_constraints/CT*.cpp")

foreach(ct ${SRCS})
    string(REGEX MATCH "CT_[A-Z_0-9]+" ct_name ${ct})
    file(APPEND ${BUILD_DEF_FILE} "#define ${ct_name}_ABC\n")
#    add_definitions(-D${ct_name}_ABC)
endforeach()



list(APPEND SRCS ${PROJECT_SOURCE_DIR}/minion/BuildVariables.cpp
                 ${PROJECT_SOURCE_DIR}/minion/BuildCSP.cpp
                 ${PROJECT_SOURCE_DIR}/minion/commandline_parse.cpp
                 ${PROJECT_SOURCE_DIR}/minion/lock.cpp
                 ${PROJECT_SOURCE_DIR}/minion/debug_functions.cpp
                 ${PROJECT_SOURCE_DIR}/minion/get_info.cpp
#                ${PROJECT_SOURCE_DIR}/minion/minion.cpp
                 ${PROJECT_SOURCE_DIR}/minion/globals.cpp
                 ${PROJECT_SOURCE_DIR}/minion/preprocess.cpp
                 ${PROJECT_SOURCE_DIR}/minion/build_constraints/BuildStaticStart.cpp
                 ${PROJECT_SOURCE_DIR}/minion/system/trigger_timer.cpp
                 ${PROJECT_SOURCE_DIR}/minion/system/sha1.cpp
                 ${PROJECT_SOURCE_DIR}/minion/help/help.cpp
                 ${PROJECT_SOURCE_DIR}/minion/inputfile_parse/inputfile_parse.cpp
                 ${PROJECT_SOURCE_DIR}/minion/dump_state.cpp
)

set(BINARY minion)

find_program(UNAME uname)
if(UNAME)
    execute_process(COMMAND ${UNAME}
            OUTPUT_VARIABLE UNAME_OUT OUTPUT_STRIP_TRAILING_WHITESPACE)
    if(UNAME_OUT STREQUAL "Darwin")
        set(APPLEMAC 1)
    elseif(UNAME_OUT MATCHES "CYGWIN.*")
        set(WINCYGWIN 1)
    elseif(UNAME_OUT MATCHES "SunOS.*")
        set(SUNOS 1)
    endif()
endif()

if(SUNOS AND NOT CMAKE_COMPILER_IS_GNUCXX)
    set(CFLAGS "${CFLAGS} -library=stlport4")
    message(STATUS "Setting Sun Studio flags")
endif()

if(WINCYGWIN)
    set(CFLAGS "${CFLAGS} -enable-auto-import")
endif()

option(THREADSAFE "" OFF)
if(THREADSAFE)
    message(STATUS "Compiling thread-safe minion")
    set(BINARY "${BINARY}-threads")
    add_definitions(-DTHREADSAFE)
endif()

option(DOMAINS64 "" ON)
if(DOMAINS64)
    add_definitions(-DDOMAINS64)
else()
set(BINARY "${BINARY}-dom32")
endif()

#if(NAUTY)
#  message(STATUS "Please be naughty")
#  add_definitions(-DUSE_NAUTY)
#  list(APPEND SRCS ${PROJECT_SOURCE_DIR}/nauty24b7/nautycxx.cpp
#                   ${PROJECT_SOURCE_DIR}/nauty24b7/nauty.c
#                   ${PROJECT_SOURCE_DIR}/nauty24b7/nautil.c
#                   ${PROJECT_SOURCE_DIR}/nauty24b7/nausparse.c)
#endif()

# This used to be in Static, but there is no reason not to always do it

if(MACRELEASE)
    set(CFLAGS "${CFLAGS} -ffast-math")
endif()

if(APPLEMAC)
#    add_definitions(-mmacosx-version-min=10.5)
#    set(CMAKE_EXE_LINKER_FLAGS ${CMAKE_EXE_LINKER_FLAGS} -mmacosx-version-min=10.5 )
 endif()

option(STATIC "" OFF)
if(STATIC)
    message(STATUS "Building static binary")
    set(BINARY "${BINARY}-static")
    if(NOT APPLEMAC)
        set(CMAKE_EXE_LINKER_FLAGS "-static")
    endif()
    if(MINGW OR MSYS)
	set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -static-libgcc -static-libstdc++")
	set(CFLAGS "${CFLAGS} -static-libgcc -static-libstdc++")
    endif()
endif()

find_program(CCONTROL ccontrol)
if(CCONTROL)
    message(STATUS "Using ccontrol")
    set(CMAKE_CXX_COMPILER_ARG1 ${CMAKE_CXX_COMPILER})
    set(CMAKE_CXX_COMPILER ${CCONTROL})
else()
    find_program(CCACHE ccache)

    option(DISTCC "" OFF)
    if(DISTCC)
        find_program(DISTCC-P distcc)
        if(DISTCC-P)
            message(STATUS "Using distcc")
            if(CCACHE)
                set(ENV{CCACHE_PREFIX} "distcc")
            else()
                set(CMAKE_CXX_COMPILER ${DISTCC-P})
            endif()
        endif()
    endif()

    if(CCACHE)
        message(STATUS "Using ccache")
        set(CMAKE_CXX_COMPILER_ARG1 ${CMAKE_CXX_COMPILER})
        set(CMAKE_CXX_COMPILER ${CCACHE})
    endif()
endif()

#check_cxx_compiler_flag("-std=c++0x" CheckForCXX)
#if(CheckForCXX)
#   message(STATUS "Compiler supports C++0x!")
#   add_definitions("-std=c++0x")
#else()
#   message(STATUS "Compiler does not support C++0x")
#endif()

set(WFLAGS "-W -Wall -Wno-missing-braces -Wno-unused-parameter")
check_cxx_compiler_flag(${WFLAGS} CheckWarnings)
if(CheckWarnings AND NOT SUNOS AND UNIX )
    message(STATUS "Warning flags: ${WFLAGS}")
    add_definitions(${WFLAGS})
endif()

option(DEBUG "" OFF)
option(SLOW_DEBUG "" OFF)
if(SLOW_DEBUG)
    message(STATUS "Enabling slow DEBUG")
    set(BINARY ${BINARY}-debugextra)
    add_definitions(-DSLOW_DEBUG)
    set(DEBUG TRUE)
endif()

option(PRINT "" OFF)
if(DEBUG)
    message(STATUS "Enabling DEBUG")
    set(BINARY "${BINARY}-debug")
    if(PRINT)
        message(STATUS "Enabling PRINT")
        add_definitions(-D_GLIBCXX_DEBUG -DMINION_DEBUG -DMINION_DEBUG_PRINT -DMORE_SEARCH_INFO)
    else()
        add_definitions(-D_GLIBCXX_DEBUG -DMINION_DEBUG -DMORE_SEARCH_INFO)
    endif()
endif()




if(WIN32 AND NOT CYGWIN AND NOT MINGW AND NOT MSYS)
    set(CFLAGS "${CFLAGS} /EHsc")
endif()

option(PROFILE "" OFF)
option(GPROF "" OFF)
option(UNOPTIMISED "" OFF)

if(PROFILE)
    message(STATUS "Enabling profiling")
    set(BINARY "${BINARY}-profile")
    set(CFLAGS "${CFLAGS} -g -fno-inline -fno-inline-functions -O")
    if(GPROF)
        message(STATUS "Enabling gprof")
        set(CFLAGS "${CFLAGS} -pg")
    endif()
    set(UNOPTIMISED ON) # Profiing can't cope with -fomit-frame-pointer
endif()


option(COVERAGE "" OFF)
if(COVERAGE)
  set(CFLAGS "${CFLAGS} -ftest-coverage -fprofile-arcs")
  set(BINARY "${BINARY}-coverage")
  set(UNOPTIMISED TRUE)
endif()

if(UNOPTIMISED)
    message(STATUS "Building unoptimised binary")
    set(BINARY "${BINARY}-unoptimised")
    set(CFLAGS "${CFLAGS} -g")
else()
    # apparently fomit-frame-pointer is only automatically enabled for O* levels
    # on machines where this doesn't affect debugging
    if(WIN32 AND NOT CYGWIN AND NOT MINGW AND NOT MSYS)
        set(CFLAGS "${CFLAGS} /O2")
    elseif(WIN32 AND (MINGW OR MSYS))
        set(CFLAGS "${CFLAGS} -O2")
    elseif(SUNOS AND NOT CMAKE_COMPILER_IS_GNUCXX)
        set(CFLAGS "${CFLAGS} -O3")
    else()
        set(CFLAGS "${CFLAGS} -O3 -fomit-frame-pointer")
        check_cxx_compiler_flag("-mdynamic-no-pic" MAC_MDYNAMIC)
        if(MAC_MDYNAMIC)
            set(CFLAGS "${CFLAGS} -mdynamic-no-pic")
        endif()
    endif()
endif()



option(SMALL "" OFF)
if(SMALL)
    message(STATUS "Building small binary")
    set(BINARY "${BINARY}-small")
    set(CFLAGS "${CFLAGS} -Os")
endif()

option(WDEG "" OFF)
if(WDEG)
    message(STATUS "Enabling wdeg heuristics")
    set(BINARY "${BINARY}-wdeg")
    add_definitions(-DWDEG)
endif()

option(WTRIG "" OFF)
if(WTRIG)
    message(STATUS "Enabling weighted triggers")
    set(BINARY "${BINARY}-wtrig")
    add_definitions(-DWEIGHTED_TRIGGERS)
endif()

option(INFO "" OFF)
if(INFO)
    message(STATUS "Enabling INFO")
    set(BINARY "${BINARY}-info")
    add_definitions(-DMORE_SEARCH_INFO)
endif()

option(DUMMYTRIG "" OFF)

if(DUMMYTRIG)
    message(STATUS "Dummy Trigger")
    add_definitions(-DNO_DYN_CHECK)
    add_definitions(-DDUMMY_TRIG)
endif()

if(BROKENTRIG)
    message(STATUS "Warning: Broken global triggers!")
    add_definitions(-DNO_DYN_CHECK)
endif()

option(QUICK "" OFF)
if(QUICK)
    message(STATUS "Enabling quick compilation")
    set(BINARY "${BINARY}-quick")
    add_definitions(-DQUICK_COMPILE)
endif()

option(REENTER "" OFF)
if(REENTER)
    message(STATUS "Enabling REENTER")
    set(BINARY "${BINARY}-reenter")
    add_definitions(-DREENTER)
endif()

option(SUPPORTSGACELEMENT "" OFF)
if(SUPPORTSGACELEMENT)
    message(STATUS "Enabling SUPPORTSGACELEMENT")
    set(BINARY "${BINARY}-igac-element")
    add_definitions(-DSUPPORTSGACELEMENT)
endif()

option(SUPPORTSGACELEMENTLONG "" OFF)
if(SUPPORTSGACELEMENTLONG)
    message(STATUS "Enabling SUPPORTSGACELEMENTLONG")
    set(BINARY "${BINARY}-igac-elementlong")
    add_definitions(-DSUPPORTSGACELEMENTLONG)
endif()

option(SUPPORTSGACLEX "" OFF)
if(SUPPORTSGACLEX)
    message(STATUS "Enabling SUPPORTSGACLEX")
    set(BINARY "${BINARY}-igac-lex")
    add_definitions(-DSUPPORTSGACLEX)
endif()

option(SUPPORTSGACLEXLONG "" OFF)
if(SUPPORTSGACLEXLONG)
    message(STATUS "Enabling SUPPORTSGACLEXLONG")
    set(BINARY "${BINARY}-igac-lexlong")
    add_definitions(-DSUPPORTSGACLEXLONG)
endif()

option(SUPPORTSGACSQUAREPACK "" OFF)
if(SUPPORTSGACSQUAREPACK)
    message(STATUS "Enabling SUPPORTSGACSQUAREPACK")
    set(BINARY "${BINARY}-igac-squarepack")
    add_definitions(-DSUPPORTSGACSQUAREPACK)
endif()

option(SUPPORTSGACSQUAREPACKLONG "" OFF)
if(SUPPORTSGACSQUAREPACKLONG)
    message(STATUS "Enabling SUPPORTSGACSQUAREPACKLONG")
    set(BINARY "${BINARY}-igac-squarepacklong")
    add_definitions(-DSUPPORTSGACSQUAREPACKLONG)
endif()

option(SUPPORTSGACLIST "" OFF)
if(SUPPORTSGACLIST)
    message(STATUS "Enabling SUPPORTSGACLIST")
    set(BINARY "${BINARY}-igac-list")
    add_definitions(-DSUPPORTSGACLIST)
endif()

option(SUPPORTSGACNDLIST "" OFF)
if(SUPPORTSGACNDLIST)
    message(STATUS "Enabling SUPPORTSGACNDLIST")
    set(BINARY "${BINARY}-igac-ndlist")
    add_definitions(-DSUPPORTSGACNDLIST)
endif()

option(SUPPORTSGACLISTNOCOPY "" OFF)
if(SUPPORTSGACLISTNOCOPY)
    message(STATUS "Enabling SUPPORTSGACLISTNOCOPY")
    set(BINARY "${BINARY}-igac-listnc")
    add_definitions(-DSUPPORTSGACLISTNOCOPY)
endif()

if(DEFINED ENV{CPU})
    message(STATUS "Using CPU-specific flags $ENV{CPU}")
    set(CFLAGS "${CFLAGS} $ENV{CPU}")
else()
    message(STATUS "No CPU-specific compiler flags configured")
endif()
set(CMAKE_CXX_FLAGS ${CFLAGS})
set(CMAKE_C_FLAGS ${CFLAGS})

if(NAME)
    set(BINARY ${NAME})
endif()
message(STATUS "Executable name set to \"${BINARY}\"")

add_library(${BINARY}-lib STATIC EXCLUDE_FROM_ALL ${SRCS})

add_executable(${BINARY} ${SRCS} ${PROJECT_SOURCE_DIR}/minion/minion.cpp)

# when we're building minion-something, add a "minion" target that does the sa~
if(NOT ${BINARY} STREQUAL minion)
    add_custom_target(minion DEPENDS ${BINARY})
    add_custom_target(minion-lib DEPENDS ${BINARY}-lib)
endif()

# generators
add_executable(bibd "${PROJECT_SOURCE_DIR}/generators/Bibd/MinionBIBDInstanceGenerator.cpp")
add_executable(golomb "${PROJECT_SOURCE_DIR}/generators/Golomb/GolombMinionGenerator.cpp")
add_executable(graceful "${PROJECT_SOURCE_DIR}/generators/Graceful/GracefulMinionGenerator.cpp")
add_executable(indicator "${PROJECT_SOURCE_DIR}/generators/indicator/indicator.cpp")
add_executable(langford "${PROJECT_SOURCE_DIR}/generators/Langford/langford.cpp")
add_executable(nqueens "${PROJECT_SOURCE_DIR}/generators/nqueens-JFP/nqueensgen.cpp")
add_executable(primequeens "${PROJECT_SOURCE_DIR}/generators/PrimeQueens/MinionPrimeQueenInstanceGenerator.cpp")
add_executable(solitaire "${PROJECT_SOURCE_DIR}/generators/Solitaire/solitaire-solver.cpp")
add_executable(sports "${PROJECT_SOURCE_DIR}/generators/SportsSchedule/MinionSportsInstanceGenerator.cpp")
add_executable(steelmill "${PROJECT_SOURCE_DIR}/generators/Steelmill/steelmill-solver.cpp")

add_custom_target(generate DEPENDS bibd golomb graceful indicator langford
                   nqueens primequeens solitaire steelmill sports)

# lisp stuff
find_program(CLISP clisp)
if(CLISP)
    message(STATUS "Clisp found, generating LISP targets")
    set(CLISP_FLAGS "-q -q -C -x")
    add_custom_target(minion-helper ${CLISP} ${CLISP_FLAGS}
        "(clisp-make-executable \"minion-helper\")"
        -i "${PROJECT_SOURCE_DIR}/generators/MinionHelper.lsp"
        WORKING_DIRECTORY ${PROJECT_BINARY_DIR}
        COMMENT "minion-helper" VERBATIM)
    add_custom_target(minion-sat ${CLISP} ${CLISP_FLAGS}
        "(clisp-make-executable \"minion-sat\" (function clisp-toplevel-sat))"
        -i "${PROJECT_SOURCE_DIR}/generators/MinionHelper.lsp"
        -i "${PROJECT_SOURCE_DIR}/generators/SAT/MinionDimacsSAT.lsp"
        WORKING_DIRECTORY ${PROJECT_BINARY_DIR}
        COMMENT "minion-sat" VERBATIM)
    add_custom_target(minion-quasigroup ${CLISP} ${CLISP_FLAGS}
        "(clisp-make-executable \"minion-quasigroup\" (function clisp-toplevel-quasigroup))"
        -i "${PROJECT_SOURCE_DIR}/generators/MinionHelper.lsp"
        -i "${PROJECT_SOURCE_DIR}/generators/Quasigroup/MinionQuasigroup.lsp"
        WORKING_DIRECTORY ${PROJECT_BINARY_DIR}
        COMMENT "minion-quasigroup" VERBATIM)
    add_custom_target(lisp-generate WORKING_DIRECTORY ${PROJECT_BINARY_DIR})
    add_dependencies(lisp-generate minion-helper minion-sat minion-quasigroup)
    set_directory_properties(PROPERTY ADDITIONAL_MAKE_CLEAN_FILES
        "${PROJECT_BINARY_DIR}/minion-helper;${PROJECT_BINARY_DIR}/minion-sat;${PROJECT_BINARY_DIR}/minion-quasigroup")
else()
    message(STATUS "No clisp executable found, not generating LISP targets")
endif()

# help
# no need to check for bash again, if it's a UNIX system the check will have
# happened already
if(UNIX AND BASH)
    message(STATUS "Generating HTML documentation target")
    add_custom_target(htmlhelp ${BASH} "docs/genhelp/genhelp.sh" "minion"
              WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}"
              COMMENT "htmlhelp" VERBATIM)
else()
    message(STATUS "You need a UNIX-like system to generate the documentation")
endif()

find_program(DOXYGEN doxygen)
if(DOXYGEN)
    message(STATUS "Generating doxygen documentation target")
    add_custom_target(api-doc ${DOXYGEN}
              WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}/minion"
              COMMENT "api-doc" VERBATIM)
else()
    message(STATUS "Doxygen not found, not generating doxygen documentation target")
endif()

add_custom_target(test-instances-quick DEPENDS ${BINARY}
                  WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}/test_instances"
                  COMMAND "./do_basic_tests.sh" "${PROJECT_BINARY_DIR}/${BINARY}" VERBATIM)

add_custom_target(test-instances-slow DEPENDS ${BINARY}
                  WORKING_DIRECTORY "${PROJECT_SOURCE_DIR}/test_instances"
                  COMMAND "./run_tests.sh" "${PROJECT_BINARY_DIR}/${BINARY}" VERBATIM)

add_custom_target(test-random-quick DEPENDS ${BINARY}
                 WORKING_DIRECTORY ${PROJECT_SOURCE_DIR}
                 COMMAND "./mini-scripts/testallconstraints.py"
                    "--numtests=25" "--procs=2"
                    "--minion=${PROJECT_BINARY_DIR}/${BINARY}" VERBATIM)

add_custom_target(test-random-slow DEPENDS ${BINARY}
                  WORKING_DIRECTORY ${PROJECT_SOURCE_DIR}
                  COMMAND "./mini-scripts/testallconstraints.py"
                    "--numtests=160" "--procs=2"
                    "--minion=${PROJECT_BINARY_DIR}/${BINARY}" VERBATIM)

add_custom_target(test-resume DEPENDS ${BINARY}
                  WORKING_DIRECTORY ${PROJECT_SOURCE_DIR}
                  COMMAND "./mini-scripts/test-resume.sh"
                   "${PROJECT_BINARY_DIR}/${BINARY}" VERBATIM)

add_custom_target(test-quick)
add_dependencies(test-quick test-instances-quick)
add_custom_target(test-standard)
add_dependencies(test-standard test-instances-slow test-random-quick test-resume)
add_custom_target(test-slow)
add_dependencies(test-slow test-instances-slow test-random-slow test-resume)
