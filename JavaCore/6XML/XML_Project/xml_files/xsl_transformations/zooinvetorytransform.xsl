<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xs:element name="inventory">
		<xs:complexType>
			<xs:sequence>
				<xs:element maxOccurs="unbounded" ref="animal" />
			</xs:sequence>
		</xs:complexType>
	</xs:element>

	<xs:element name="animal">
		<xs:complexType>
			<xs:sequence>
				<xs:element ref="name" />
				<xs:element name="species" type="xs:string" />
				<xs:element name="habitat" type="xs:string" />
				<xs:choice>
					<xs:element name="food" type="xs:string" />
					<xs:element ref="foodRecipe" />
				</xs:choice>
				<xs:element name="temperament" type="xs:string" />
				<xs:element name="weight" type="xs:double" />
			</xs:sequence>
			<xs:attribute name="animalClass" default="unknown">
				<xs:simpleType>
					<xs:restriction base="xs:token">
						<xs:enumeration value="unknown" />
						<xs:enumeration value="mammal" />
						<xs:enumeration value="reptile" />
						<xs:enumeration value="bird" />
					</xs:restriction>
				</xs:simpleType>
			</xs:attribute>
		</xs:complexType>
	</xs:element>

	<xs:element name="foodRecipe">
		<xs:complexType>
			<xs:sequence>
				<xs:element ref="name" />
				<xs:element maxOccurs="unbounded" name="ingredient" type="xs:string" />
			</xs:sequence>
		</xs:complexType>
	</xs:element>
</xs:schema>