#ifndef PLANG_DIALECT_PLANGOPS
#define PLANG_DIALECT_PLANGOPS

include "mlir/IR/OpBase.td"

def Plang_Dialect : Dialect {
  let name = "plang";
  let cppNamespace = "::plang::mlir";
  let summary = "Operations for data transformation and analysis DSL lowering";
  let description = [{
    TODO: Add high-level operations for map, filter, reduce, joins, records,
    gradual typing boundaries, and package-level module metadata.
  }];
}

class Plang_Op<string mnemonic, list<Trait> traits = []>
    : Op<Plang_Dialect, mnemonic, traits>;

def Plang_MapOp : Plang_Op<"map"> {
  let summary = "Apply a transformation function to each record in a collection";
  let description = [{
    TODO: Define operands, results, regions, parser, printer, and verifier.
  }];
}

#endif // PLANG_DIALECT_PLANGOPS
